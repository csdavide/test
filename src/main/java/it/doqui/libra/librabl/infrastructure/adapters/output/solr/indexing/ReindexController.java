package it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing;

import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.application.model.jobs.responses.ReindexJobResult;
import it.doqui.libra.librabl.application.ports.out.JobRepository;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import it.doqui.libra.librabl.application.ports.out.ReindexPort;
import it.doqui.libra.librabl.domain.model.exceptions.AbortException;
import it.doqui.libra.librabl.domain.model.graph.IndexingFlags;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeLoaderDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.QueryContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

@ApplicationScoped
@Slf4j
public class ReindexController implements ReindexPort {

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Inject
    Indexer indexer;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @Inject
    MessageSenderPort producer;

    @Inject
    JobRepository jobRepository;

    @Inject
    SessionContext sessionContext;

    @Override
    public void syncReindexTransactions(TenantRef tenantRef, Collection<Long> transactions) {
        tenantRef = authenticationManagerPort.autenticateIfRequired(tenantRef, true);
        indexer.reindexTransactions(tenantRef.toString(), null, transactions, IndexingFlags.FULL_FLAG_MASK, null, null, true, false);
    }

    @Override
    public void syncReindexNodes(TenantRef tenantRef, Collection<String> uuids) {
        tenantRef = authenticationManagerPort.autenticateIfRequired(tenantRef, true);
        var qc = QueryContext.builder()
                .tenant(sessionContext.getTenant())
                .schema(sessionContext.getUserContext().getDbSchema())
                .build();
        var r = nodeLoaderDAO.lookupNodes(uuids.stream().map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
        for (var uuid : uuids) {
            var n = r.getNode(new Vertex(VertexType.UUID, uuid));
            if (n != null) {
                performReindexSubTree(null, tenantRef.toString(), n.getId(), 40, false);
            }
        }
    }

    @Override
    public ReindexJobResult reindexSubTree(Vertex node, int blockSize, boolean recursive) {
        long nodeId;
        if (node.getType() != VertexType.ID) {
            var qc = QueryContext.builder()
                    .tenant(sessionContext.getTenant())
                    .schema(sessionContext.getUserContext().getDbSchema())
                    .build();
            nodeId = nodeLoaderDAO.getNode(node, qc)
                    .map(ActiveNode::getId)
                    .orElseThrow(() -> new NotFoundException("Node " + node + " not found."));
        } else {
            nodeId = Long.parseLong(node.getValue());
        }

        var tenant = sessionContext.getTenant();
        final var jobId = sessionContext.getJobId();
        return performReindexSubTree(jobId, tenant, nodeId, blockSize, recursive);
    }

    private ReindexJobResult performReindexSubTree(String jobId, String tenant, long nodeId, int blockSize, boolean recursive) {
        var schema = sessionContext.getUserContext().getDbSchema();
        return DBUtils.call(ds, schema, conn -> {
            final String sql;
            if (recursive) {
                sql = """
                    select distinct x.tx\s
                    from (\s
                      select n.tx\s
                      from ecm_paths p\s
                      join ecm_nodes n on p.node_id = n.id\s
                      where p.path_parts @> ? and p.is_hard and n.tenant = ?\s
                      union\s
                      select n.tx\s
                      from ecm_paths p\s
                      join ecm_nodes n on p.node_id = n.id\s
                      join ecm_security_groups s on s.id = n.sg_id\s
                      where p.path_parts @> ? and p.is_hard and n.tenant = ?\s
                    ) x
                    """;
            } else {
                sql = """
                    select distinct x.tx\s
                    from (\s
                      select distinct n.tx\s
                      from ecm_nodes n\s
                      where n.id = ? and n.tenant = ?\s
                      union\s
                      select distinct s.tx\s
                      from ecm_nodes n\s
                      join ecm_security_groups s on s.id = n.sg_id\s
                      where n.id = ? and n.tenant = ?\s
                    ) x
                    """;
            }

            try {
                var total = 0L;
                try (PreparedStatement stmt = conn.prepareStatement(sql.replace("distinct x.tx", "count(distinct x.tx)"))) {
                    setSubTreeQueryParams(conn, stmt, tenant, nodeId, recursive);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            total = rs.getLong(1);
                        }
                    }
                }

                if (jobId != null) {
                    long totalExpectedTransactions = total;
                    jobRepository.updateJob(jobId, (result) -> {
                        var r = new ReindexJobResult();
                        r.setIndexedTransactions(0);
                        r.setTotalExpectedTransactions(totalExpectedTransactions);
                        return r;
                    });
                }

                var count = 0L;
                if (total > 0) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        setSubTreeQueryParams(conn, stmt, tenant, nodeId, recursive);
                        try (ResultSet rs = stmt.executeQuery()) {
                            var transactions = new ArrayList<Long>();
                            while (rs.next()) {
                                if (sessionContext.getCancelled().get()) {
                                    throw new AbortException("Reindex cancelled: job " + jobId);
                                }

                                long tx = rs.getLong("tx");
                                count++;
                                transactions.add(tx);
                                if (transactions.size() >= blockSize) {
                                    reindexWithOperation(jobId, tenant, transactions, count);
                                    transactions.clear();
                                }
                            }

                            if (!transactions.isEmpty()) {
                                reindexWithOperation(jobId, tenant, transactions, count);
                            }
                        }

                    }
                } // end if total > 0

                log.info("{} transaction{} re-indexed{} on tenant {}", count, count == 1 ? "" : "s", recursive ? " recursively" : "", tenant);
                var result = new ReindexJobResult();
                result.setIndexedTransactions(count);
                result.setTotalExpectedTransactions(total);
                return result;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void setSubTreeQueryParams(Connection conn, PreparedStatement stmt, String tenant, long nodeId, boolean recursive) throws SQLException {
        if (recursive) {
            var parts = new Long[1];
            parts[0] = nodeId;

            stmt.setArray(1, conn.createArrayOf("INTEGER", parts));
            stmt.setString(2, tenant);
            stmt.setArray(3, conn.createArrayOf("INTEGER", parts));
            stmt.setString(4, tenant);
        } else {
            stmt.setLong(1, nodeId);
            stmt.setString(2, tenant);
            stmt.setLong(3, nodeId);
            stmt.setString(4, tenant);
        }
    }

    private void reindexWithOperation(String jobId, String tenant, Collection<Long> txIds, long count) {
        DBUtils.doInTransaction(ds, sessionContext.getUserContext().getDbSchema(),
                conn -> {
                    indexer.reindexTransactions(conn, tenant, txIds, IndexingFlags.FULL_FLAG_MASK, null, null, true, false).forEach(producer::submit);
                    if (jobId != null) {
                        jobRepository.updateJob(jobId, (result) -> {
                            if (result instanceof ReindexJobResult r) {
                                r.setIndexedTransactions(count);
                                return r;
                            }

                            return null;
                        });
                    }
                }
        );
    }
}
