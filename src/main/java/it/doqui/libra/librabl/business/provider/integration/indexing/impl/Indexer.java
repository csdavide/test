package it.doqui.libra.librabl.business.provider.integration.indexing.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.agroal.api.AgroalDataSource;
import io.quarkus.tika.TikaParser;
import it.doqui.libra.librabl.business.provider.data.entities.AccessRule;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.NodePath;
import it.doqui.libra.librabl.business.provider.data.entities.SecurityGroup;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.provider.integration.indexing.ReindexTask;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.provider.integration.solr.AbstractSolrController;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.provider.search.ISO9075;
import it.doqui.libra.librabl.business.provider.stats.StatController;
import it.doqui.libra.librabl.business.provider.stats.StatMeasure;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.interfaces.ContentStoreService;
import it.doqui.libra.librabl.business.service.interfaces.ManagementService;
import it.doqui.libra.librabl.business.service.node.PropertyContainer;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.management.MgmtOperation;
import it.doqui.libra.librabl.views.node.ContentProperty;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.views.schema.PropertyDescriptor;
import it.doqui.libra.librabl.views.tenant.TenantData;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.util.TriConsumer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.*;
import static it.doqui.libra.librabl.business.provider.integration.messaging.MessagePriority.VERYHIGH_PRIORITY;
import static it.doqui.libra.librabl.business.provider.mappers.PropertyConverter.*;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;

@ApplicationScoped
@Slf4j
public class Indexer extends AbstractSolrController {

    @ConfigProperty(name = "solr.fullTextSizeThreshold", defaultValue = "0")
    long fullTextSizeThreshold;

    @ConfigProperty(name = "solr.indexer.pagination.addNodesPageSize", defaultValue = "50")
    int addNodesPageSize;

//    @ConfigProperty(name = "solr.indexer.pagination.securityGroupsPageSize", defaultValue = "100")
//    int securityGroupsPageSize;

    @ConfigProperty(name = "solr.indexer.pagination.removeNodesPageSize", defaultValue = "100")
    int removeNodesPageSize;

    @ConfigProperty(name = "solr.indexer.includeUnknownProperties", defaultValue = "false")
    boolean includeUnknownProperties;

    @ConfigProperty(name = "libra.reindex.queue", defaultValue = "tasks")
    String queueName;

    @ConfigProperty(name = "libra.reindex.time-before-clean", defaultValue = "168h")
    Duration timeBeforeClean;

    @Inject
    ModelManager modelManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ContentStoreService contentStoreManager;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @Inject
    IndexerDAO dao;

    @Inject
    TaskProducer producer;

    @Inject
    TikaParser tika;

    @Inject
    StatController statController;

    @Inject
    ManagementService managementService;

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    TenantDataManager tenantDataManager;

    public void reindexTransactions(String taskId, String tenant, String dbSchema, Collection<Long> txIds, int flags, Set<String> includedUUIDs, Set<String> excludedUUIDs, boolean async, boolean completed, boolean addOnly) {
        try {
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.RUNNING, null);
            DBUtils.doInTransaction(ds, Optional.ofNullable(dbSchema).orElse(UserContextManager.getContext().getDbSchema()),
                conn -> reindex(conn, tenant, txIds, flags, includedUUIDs, excludedUUIDs, async, completed, addOnly)
                    .forEach(producer::submit)
            );
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.SUCCESS, null);
        } catch (RuntimeException e) {
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.FAILED, Map.of("message", e.getMessage()));
            throw e;
        }
    }

    public void reindexNodes(String tenant, Collection<Long> nodeIds, int flags, boolean addOnly) {
        DBUtils.doInTransaction(ds, UserContextManager.getContext().getDbSchema(),
            conn -> reindexNodes(conn, tenant, nodeIds, flags, true, addOnly)
                .forEach(producer::submit)
        );
    }

    public void reindexSubTree(String taskId, String tenant, long nodeId, int flags, boolean addOnly, int blockSize, boolean recursive) {
        try {
            performReindexSubTree(taskId, tenant, nodeId, flags, addOnly, blockSize, recursive);
        } catch (RuntimeException e) {
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.FAILED, Map.of("message", e.getMessage()));
            throw e;
        }
    }

    private void performReindexSubTree(String taskId, String tenant, long nodeId, int flags, boolean addOnly, int blockSize, boolean recursive) {
        var schema = UserContextManager.getContext().getDbSchema();
        DBUtils.call(ds, schema, conn -> {
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

                asyncOperationService.completeTask(taskId, AsyncOperation.Status.RUNNING, Map.of("total", total, "count", 0));

                var count = 0L;
                if (total > 0) {
                    asyncOperationService.completeTask(taskId, AsyncOperation.Status.RUNNING, Map.of("total", total, "count", 0));
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        setSubTreeQueryParams(conn, stmt, tenant, nodeId, recursive);
                        try (ResultSet rs = stmt.executeQuery()) {
                            var transactions = new ArrayList<Long>();
                            while (rs.next()) {
                                long tx = rs.getLong("tx");
                                count++;
                                transactions.add(tx);
                                if (transactions.size() >= blockSize) {
                                    reindexWithOperation(taskId, tenant, transactions, flags, addOnly, count);
                                    transactions.clear();
                                }
                            }

                            if (!transactions.isEmpty()) {
                                reindexWithOperation(taskId, tenant, transactions, flags, addOnly, count);
                            }
                        }

                    }
                } // end if total > 0

                log.info("{} transaction{} re-indexed{} on tenant {}", count, count == 1 ? "" : "s", recursive ? " recursively" : "", tenant);
                asyncOperationService.completeTask(taskId, AsyncOperation.Status.SUCCESS, Map.of("total", total, "count", count));
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
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

    private void reindexWithOperation(String taskId, String tenant, Collection<Long> txIds, int flags, boolean addOnly, long count) {
        DBUtils.doInTransaction(ds, UserContextManager.getContext().getDbSchema(),
            conn -> {
                reindex(conn, tenant, txIds, flags, null, null, true, true, addOnly)
                    .forEach(producer::submit);
                asyncOperationService.completeTask(taskId, AsyncOperation.Status.RUNNING, Map.of("count", count));
            }
        );
    }

    private List<ReindexTask> reindexNodes(Connection conn, String tenant, Collection<Long> nodeIds, int flags, boolean async, boolean addOnly) {
        final StatMeasure sm = new StatMeasure();
        sm.step("total", System.currentTimeMillis());
        var txMap = new HashMap<Long, ApplicationTransaction>();
        try {
            var collection = collectionName(TenantRef.valueOf(tenant));

            final List<ReindexTask> textAsyncTasks = new LinkedList<>();
            var registerFullTextSQL = "insert into ecm_files (tenant,contentref,cached_text,error_msg) values (?,?,?,?) " +
                "on conflict (tenant,contentref) do update set cached_text = excluded.cached_text, error_msg = excluded.error_msg";
            try (var stmt = conn.prepareStatement(registerFullTextSQL)) {
                // reindex active nodes
                long t0 = System.currentTimeMillis();
                var nodes = dao.findNodes(conn, nodeIds, txMap, UserContextManager.getTenantData().map(TenantData::isIndexingDisabled).orElse(false));
                var sgMap = dao.mapSecurityGroups(conn, nodes);
                sm.add("dbread", System.currentTimeMillis() - t0);
                var counter = new AtomicInteger(0);
                textAsyncTasks.addAll(reindex(collection, nodes, txMap, sgMap, flags, async, sm, (contentUrl, text, emsg) -> {
                    try {
                        stmt.setString(1, tenant);
                        stmt.setString(2, contentUrl);
                        stmt.setString(3, text);
                        stmt.setString(4, emsg == null ? null : (emsg.length() > 255 ? emsg.substring(0, 255) : emsg));
                        stmt.addBatch();
                        counter.incrementAndGet();
                    } catch (SQLException e) {
                        throw new SystemException(e);
                    }
                }));

                if (counter.get() > 0) {
                    stmt.executeBatch();
                }

                if (!retroCompatibilityMode) {
                    // reindex security groups
                    t0 = System.currentTimeMillis();
                    var sgs = dao.findSGOfNodes(conn, tenant, nodeIds, txMap);
                    sm.add("dbread", System.currentTimeMillis() - t0);
                    reindex(collection + "-sg", sgs, sm);
                }

                if (!addOnly) {
                    // remove archived nodes from solr
                    t0 = System.currentTimeMillis();
                    var uuids = dao.findArchivedUUIDs(conn, nodeIds);
                    sm.add("dbread", System.currentTimeMillis() - t0);
                    removeAll(collection, uuids, sm);

                    // remove removed/purged nodes from solr
                    t0 = System.currentTimeMillis();
                    uuids = dao.findRemovedUUIDs(conn, nodeIds);
                    sm.add("dbread", System.currentTimeMillis() - t0);
                    removeAll(collection, uuids, sm);
                    cleanNodes(uuids, sm);
                }

                if (!fakeIndexModeEnabled) {
                    client.commit(collection);
                    if (!retroCompatibilityMode) {
                        client.commit(collection + "-sg");
                    }
                }
            }

            return textAsyncTasks;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new SystemException(e);
        } finally {
            sm.step("total", System.currentTimeMillis());
            sm.add("tx", txMap.size());

            statController.add(sm);
        }
    }

    public List<ReindexTask> reindex(Connection conn, String tenant, Collection<Long> txIds, int flags, Set<String> includedUUIDs, Set<String> excludedUUIDs, boolean async, boolean completed, boolean addOnly) {
        final StatMeasure sm = new StatMeasure();
        sm.step("total", System.currentTimeMillis());
        Map<Long,ApplicationTransaction> txMap = null;
        try {
            log.info("Indexing transaction {} in schema {} and tenant {}", txIds, conn.getSchema(), tenant);
            txMap = dao.mapTransactions(conn, tenant, txIds);
            if (txMap.isEmpty()) {
                log.error("Unable to find transactions {} for tenant {}", txIds, tenant);
                throw new NotFoundException("TXID: " + txIds);
            }

            final List<ReindexTask> textAsyncTasks = new LinkedList<>();
            var registerFullTextSQL = "insert into ecm_files (tenant,contentref,cached_text,error_msg) values (?,?,?,?) " +
                "on conflict (tenant,contentref) do update set cached_text = excluded.cached_text, error_msg = excluded.error_msg";
            try (var stmt = conn.prepareStatement(registerFullTextSQL)) {

                String collection = collectionName(TenantRef.valueOf(tenant));

                Collection<ActiveNode> nodes;
                List<String> uuids;
                Collection<SecurityGroup> sgs;

                final Pageable pageable = new Pageable();
                pageable.setSize(addNodesPageSize);
                pageable.setPage(0);

                var ephemeralUUIDs = new LinkedList<String>();
                var deletedIDs = new LinkedList<Long>();
                boolean indexingDisabled = tenantDataManager.findByIdOptional(tenant)
                    .map(TenantSpace::getData)
                    .map(TenantData::isIndexingDisabled)
                    .orElse(UserContextManager.getTenantData().map(TenantData::isIndexingDisabled).orElse(false));
                // reindex active nodes
                do {
                    long t0 = System.currentTimeMillis();
                    nodes = dao.findNodesWithTx(conn, txMap, includedUUIDs, excludedUUIDs, pageable, indexingDisabled);
                    nodes.stream().filter(node -> node.getAspects().contains(ASPECT_ECMSYS_EPHEMERAL)).map(ActiveNode::getUuid).forEach(ephemeralUUIDs::add);
                    nodes.stream().filter(node -> node.getAspects().contains(ASPECT_ECMSYS_DELETED)).map(ActiveNode::getId).forEach(deletedIDs::add);
                    var sgMap = dao.mapSecurityGroups(conn, nodes);
                    sm.add("dbread", System.currentTimeMillis() - t0);
                    var counter = new AtomicInteger(0);
                    textAsyncTasks.addAll(reindex(collection, nodes, txMap, sgMap, flags, async, sm, (contentUrl, text, emsg) -> {
                        try {
                            stmt.setString(1, tenant);
                            stmt.setString(2, contentUrl);
                            stmt.setString(3, text);
                            stmt.setString(4, emsg == null ? null : (emsg.length() > 255 ? emsg.substring(0, 255) : emsg));
                            stmt.addBatch();
                            counter.incrementAndGet();
                            stmt.addBatch();
                            counter.incrementAndGet();
                        } catch (SQLException e) {
                            throw new SystemException(e);
                        }
                    }));

                    if (counter.get() > 0) {
                        stmt.executeBatch();
                    }

                    pageable.setPage(pageable.getPage() + 1);
                } while (nodes.size() >= pageable.getSize());

                if (!retroCompatibilityMode) {
                    long t0 = System.currentTimeMillis();
                    sgs = dao.findSGWithTx(conn, txMap, tenant, null);
                    sm.add("dbread", System.currentTimeMillis() - t0);
                    log.debug("Found SGs: {} (tenant {})", sgs.stream().map(SecurityGroup::getId).toList(), tenant);
                    var _sgs = sgs.stream().filter(sg -> !sg.getRules().isEmpty()).toList();
                    reindex(collection + "-sg", _sgs, sm);
                    removeAll(collection + "-sg", sgs.stream().filter(sg -> sg.getRules().isEmpty()).map(sg -> "" + sg.getId()).toList(), sm);
                    pageable.setPage(pageable.getPage() + 1);
                }

                if (!addOnly) {
                    if (!ephemeralUUIDs.isEmpty()) {
                        removeAll(collection, ephemeralUUIDs, sm);
                    }

                    pageable.setSize(removeNodesPageSize);
                    pageable.setPage(0);
                    // remove archived nodes from solr
                    do {
                        long t0 = System.currentTimeMillis();
                        uuids = dao.findArchivedUUIDsWithTx(conn, txMap.keySet(), pageable);
                        sm.add("dbread", System.currentTimeMillis() - t0);
                        removeAll(collection, uuids, sm);
                        pageable.setPage(pageable.getPage() + 1);
                    } while (uuids.size() >= pageable.getSize());

                    pageable.setPage(0);
                    // remove removed/purged nodes from solr
                    do {
                        long t0 = System.currentTimeMillis();
                        uuids = dao.findRemovedUUIDsWithTx(conn, txMap.keySet(), pageable);
                        sm.add("dbread", System.currentTimeMillis() - t0);
                        removeAll(collection, uuids, sm);
                        cleanNodes(uuids, sm);
                        pageable.setPage(pageable.getPage() + 1);
                    } while (uuids.size() >= pageable.getSize());
                }

                if (!fakeIndexModeEnabled) {
                    client.commit(collection);
                    if (!retroCompatibilityMode) {
                        client.commit(collection + "-sg");
                    }
                }

                if (completed) {
                    long t0 = System.currentTimeMillis();
                    if (!deletedIDs.isEmpty()) {
                        dao.removeDeletedNodes(conn, deletedIDs);
                    }

                    dao.setTransactionIndexedNow(conn, txMap.keySet());
                    sm.add("settx", System.currentTimeMillis() - t0);
                    txMap.forEach((txID,tx) -> log.info("TX ID {} indexed (tenant {})", txID, tenant));
                }
            }

            return textAsyncTasks;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new SystemException(e);
        } finally {
            sm.step("total", System.currentTimeMillis());
            if (txMap != null) {
                sm.add("tx", txMap.size());
            }

            statController.add(sm);
        }
    }

    private void reindex(String collection, Collection<SecurityGroup> sgs, final StatMeasure sm) {
        if (sgs == null || sgs.isEmpty()) {
            return;
        }

        log.debug("Indexing {} SGs into collection {}", sgs.size(), collection);
        final List<SolrInputDocument> documents = sgs.stream()
            .map(sg -> {
                SolrInputDocument document = new SolrInputDocument();
                document.addField("ID", String.valueOf(sg.getId()));
                document.addField("TX", String.valueOf(sg.getTx().getId()));
                document.addField("TSTAMP", sg.getTx().getCreatedAt().format(DateTimeFormatter.ISO_INSTANT));

                sg.getRules().stream()
                    .filter(ar -> StringUtils.startsWith(ar.getRights(), "1"))
                    .map(AccessRule::getAuthority)
                    .sorted()
                    .distinct()
                    .forEach(authority -> document.addField("AUTHORITY", authority));

                return document;
            })
            .collect(Collectors.toList());

        if (!documents.isEmpty()) {
            log.debug("Solr adding {} SG documents to collection {}", documents.size(), collection);
            long t0 = System.currentTimeMillis();
            try {
                UpdateResponse response = client.add(collection, documents);
                if (response.getStatus() != 0) {
                    String msg = String.format(
                        "Solr returned status %d indexing SGs %s",
                        response.getStatus(), documents.stream().map(document -> document.get("ID")).collect(Collectors.toList())
                    );
                    throw new SystemException(msg);
                }
            } catch (IOException | SolrServerException e) {
                throw new SystemException(e);
            } finally {
                sm.add("solr", System.currentTimeMillis() - t0);
            }
        }
    }

    private List<ReindexTask> reindex(String collection, Collection<ActiveNode> nodes, Map<Long,ApplicationTransaction> txMap, final Map<Long,Long> sgMap, int flags, boolean async, final StatMeasure sm, final TriConsumer<String,String,String> fullTextConsumer) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        final ModelSchema schema = modelManager.getContextModel();
        final Multimap<Long,String> textAsyncNodeMap = ArrayListMultimap.create();
        final List<SolrInputDocument> documents = new ArrayList<>(nodes.size());
        nodes.stream()
            .filter(node -> !node.getAspects().contains(ASPECT_ECMSYS_EPHEMERAL))
            .filter(node -> !node.getAspects().contains(ASPECT_ECMSYS_DELETED))
            .map(node -> {
                try {
                    return createDocument(schema, node, sgMap, flags, async, sm, fullTextConsumer);
                } catch (BadDataException e) {
                    log.warn("Unable to index tenant {} node {}. Got {}", node.getTenant(), node.getUuid(), e.getMessage());
                    //TODO: registrare la mancata indicizzazione sul nodo e resettare lo stato se fallito per ogni set tx indexed
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(x -> {
                ActiveNode r = x.getRight();
                if (r != null) {
                    textAsyncNodeMap.put(r.getTx().getId(), r.getUuid());
                }

                SolrInputDocument document = x.getLeft();
                if (document == null) {
                    return;
                }


                boolean update = ObjectUtils.getAsBoolean(x.getMiddle(), false);
                if (fakeIndexModeEnabled) {
                    try {
                        Map<String, Object> map = new HashMap<>();
                        for (String name : document.getFieldNames()) {
                            SolrInputField f = document.get(name);
                            map.put(name, f == null ? null : f.getValue());
                        }

                        log.debug("Generated document to {}: {}", update? "update" : "add", objectMapper.writeValueAsString(map));
                    } catch (JsonProcessingException e) {
                        throw new SystemException(e);
                    }

                    return;
                }

                documents.add(document);
            });

        if (!documents.isEmpty()) {
            log.debug("Solr adding {} node documents to collection {}", documents.size(), collection);
            long t0 = System.currentTimeMillis();
            try {
                UpdateResponse response = client.add(collection, documents);
                if (response.getStatus() != 0) {
                    String msg = String.format(
                            "Solr returned status %d indexing documents %s",
                            response.getStatus(), documents.stream().map(this::getID).collect(Collectors.toList())
                        );
                        throw new SystemException(msg);
                }
            } catch (IOException | SolrServerException e) {
                throw new SystemException(e);
            } finally {
                sm.add("solr", System.currentTimeMillis() - t0);
            }
        }

        if (!textAsyncNodeMap.isEmpty()) {
            List<ReindexTask> reindexTasks = new ArrayList<>();
            for (Long txId : textAsyncNodeMap.keySet()) {
                ApplicationTransaction tx = txMap.get(txId);
                ReindexTask m = new ReindexTask();
                m.setTaskId(tx.getUuid());
                m.setFlags(TEXT_FLAG);
                m.setTenant(tx.getTenant());
                m.setTx(tx.getId());
                m.setIncludeSet(new HashSet<>(textAsyncNodeMap.get(txId)));
                m.setCompleted(false);
                m.setPriority(VERYHIGH_PRIORITY);
                m.setQueueName(queueName);
                reindexTasks.add(m);
            }

            return reindexTasks;
        }

        return List.of();
    }

    private void cleanNodes(List<String> uuids, final StatMeasure sm) {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }

        // schedule clean of uuids at deltat > tempo di backup in config
        long t0 = System.currentTimeMillis();
        var cleanOp = new MgmtOperation();
        cleanOp.setOp(MgmtOperation.MgmtOperationType.NODECLEAN);
        var cleanOperand = new MgmtOperation.NodeCleanOperand();
        cleanOperand.getUuids().addAll(uuids);
        cleanOp.setOperand(cleanOperand);
        cleanOp.setDelay(timeBeforeClean.toMillis());
        managementService.performOperations(List.of(cleanOp));
        sm.add("cleaner", System.currentTimeMillis() - t0);
    }

    private void removeAll(String collection, List<String> uuids, final StatMeasure sm) throws SolrServerException, IOException {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }

        if (fakeIndexModeEnabled) {
            for (String uuid : uuids) {
                log.info("Should delete ID {} from collection {}", uuid, collection);
            }

            return;
        }

        log.debug("Solr removing documents {} from collection {}", uuids, collection);
        sm.add("deletedNodes", uuids.size());
        long t0 = System.currentTimeMillis();
        UpdateResponse response = client.deleteById(collection, uuids);
        sm.add("solr", System.currentTimeMillis() - t0);
        if (response.getStatus() != 0) {
            String msg = String.format(
                "Solr returned status %d removing documents", response.getStatus()
            );
            throw new SystemException(msg);
        }
    }

    private String getID(SolrInputDocument document) {
        String id = null;
        SolrInputField f = document.get("ID");
        if (f != null) {
            id = extractID((String) f.getFirstValue());
        }

        return id;
    }

    private Triple<SolrInputDocument,Boolean,ActiveNode> createDocument(ModelSchema schema, ActiveNode node, Map<Long,Long> sgMap, int flags, boolean async, final StatMeasure sm, final TriConsumer<String,String,String> fullTextConsumer) {
        sm.add("addedNodes", 1);
        // Per il momento viene forzato update completo del doc grazie a FULL_FLAG_MASK
        flags = IndexingFlags.combine(FULL_FLAG_MASK, flags, IndexingFlags.parse(node.getTransactionFlags()));
        SolrInputDocument document = new SolrInputDocument();

        boolean update = !match(flags, METADATA_FLAG);
        boolean textAsyncRequired = false;
        final Set<String> customPropNames = new HashSet<>();

        document.addField("ID", retroCompatibilityMode ? node.getURI().toString() : node.getUuid());
        document.addField("TX", field(String.valueOf(node.getTx().getId()), update));
        document.addField("TSTAMP", field(node.getTx().getCreatedAt().format(DateTimeFormatter.ISO_INSTANT), update));
        if (match(flags, METADATA_FLAG)) {
            document.addField("DBID", node.getId());

            // aspects
            node.getData().getAspects()
                .stream()
                .map(a -> map(schema, a, false))
                .forEach(a -> document.addField("ASPECT", a));

            // contents
            node.getData().getContents()
                .forEach(cp -> {
                    node.getData().getProperties().compute(cp.getName(), (k, v) -> {
                        var s = cp.toString();
                        if (v == null) {
                            return s;
                        }

                        var result = new ArrayList<String>();
                        if (v instanceof Collection<?> collection) {
                            result.addAll(collection.stream().map(Object::toString).toList());
                            result.add(s);
                        }
                        return result;
                    });
                });

            // properties
            node.getData().getProperties()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && StringUtils.isNotBlank(entry.getValue().toString()))
                .map(entry -> {
                    try {
                        return mapProperty(schema, node, entry);
                    } catch (RuntimeException e) {
                        if (async) {
                            log.error("Unable to index property {} of node {} in tenant {}: {}", entry.getKey(), node.getId(), node.getTenant(), e.getMessage(), e);
                            return null;
                        }

                        throw e;
                    }
                })
                .filter(pc -> pc != null && pc.getDescriptor().isIndexed() && pc.getValue() != null)
                .forEach(pc -> {
                    String name = "@" + map(schema, pc.getDescriptor().getName(), false);
                    Object value = pc.getValue();
                    var nt = pc.getDescriptor().isNotTokenizedFieldRequired();
                    final var tk = pc.getDescriptor().isAdditionalTokenizedFieldRequired();
                    if (value instanceof Map<?,?> map) {
                        if (pc.getDescriptor().isMultiple()) {
                            for (Object v : map.values()) {
                                addField(document, name, v, nt, tk);
                                nt = false;
                            }
                        } else {
                            var defaultValue = map.get(Locale.getDefault().toString());
                            if (defaultValue == null) {
                                defaultValue = map.values().stream()
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse(null);
                            }

                            if (defaultValue != null) {
                                addField(document, name, defaultValue, nt, tk);
                            }
                        }
                    } else if (value instanceof Collection<?> collection) {
                        if (pc.getDescriptor().isMultiple()) {
                            for (Object v : collection) {
                                addField(document, name, v, nt, tk);
                                nt = false;
                            }
                        } else {
                            var _nt = nt;
                            collection.stream().filter(Objects::nonNull).findFirst()
                                .ifPresent(v -> addField(document, name, v, _nt, tk));
                        }
                    } else {
                        addField(document, name, value, nt, tk);
                    }
                });

            // type
            document.addField("EXACTTYPE", map(schema, node.getTypeName(), true));
            schema.getTypeHierarchy(node.getTypeName())
                .forEach(t -> document.addField("TYPE", map(schema, t.getName(), true)));

        } // endif metadata

        if (match(flags, TEXT_FLAG)
            && !node.getData().getAspects().contains(ASPECT_ECMSYS_DISABLED_FULLTEXT)
            && !node.getData().getAspects().contains(ASPECT_ECMSYS_ENCRYPTED)
            && !UserContextManager.getTenantData().map(TenantData::isFullTextDisabled).orElse(false)) {
            // contents
            for (ContentProperty cp : node.getData().getContents()) {
                if (!cp.isOpaque() && cp.getContentUrl() != null && cp.getSize() > 0 && (fullTextSizeThreshold <= 0 || cp.getSize() < fullTextSizeThreshold)) {
                    var pd = schema.getProperty(cp.getName());
                    if (pd != null && pd.isIndexed()) {
                        log.trace("Processing node {} ({}) property {} content {}", node.getId(), node.getTenant(), cp.getName(), cp.getContentUrl());

                        // get text
                        String text = null;
                        long t0 = System.currentTimeMillis();
                        if (!match(flags, FORCE_FLAG)) {
                            text = cachedText(node, cp);
                        }

                        if (text == null) {
                            if (async) {
                                var r = fullText(node, cp);
                                text = r.getLeft();
                                if (fullTextConsumer != null) {
                                    fullTextConsumer.accept(cp.getContentUrl(), text, r.getRight());
                                }
                            } else {
                                textAsyncRequired = true;
                            }
                        } else {
                            log.trace("Found cached text having {} characters", text.length());
                        }
                        sm.add("fulltext", System.currentTimeMillis() - t0);

                        if (StringUtils.isNotBlank(text)) {
                            document.addField("TEXT", text);
                            if (node.getData().getAspects().contains(ASPECT_ECMSYS_LOCALIZABLE)) {
                                String locale = ObjectUtils.getAsString(node.getData().getProperties().get(PROP_ECMSYS_LOCALE));
                                if (locale != null) {
                                    String textLocaleName = "TEXT_locale_" + locale;
                                    customPropNames.add(textLocaleName);
                                    document.addField(textLocaleName, text);
                                }
                            }
                        }
                    }
                } else {
                    log.trace("Ignoring node {} property {} content {}", node.getId(), cp.getName(), cp.getContentUrl());
                }
            } // end for cp
        } // end if text

        if (match(flags, PATH_FLAG) || match(flags, METADATA_FLAG)) {
            Optional.ofNullable(node.getProperties().get(CM_NAME))
                .map(Object::toString)
                .map(StringUtils::stripToEmpty)
                .filter(StringUtils::isNotBlank)
                .ifPresent(name -> document.addField("NAME", name));

            if (!node.getParents().isEmpty()) {
                // parents
                final var isPrimaryParentProcessed = new AtomicBoolean(false);
                node.getParents().forEach(p -> {
                    var parentId = retroCompatibilityMode ? p.getParent().getURI().toString() : p.getParent().getUuid();
                    var assocTypeQname = map(schema, p.getTypeName(), true);
                    var name = StringUtils.stripToEmpty(PrefixedQName.valueOf(p.getName()).getLocalPart());
                    if (StringUtils.isNotBlank(name)) {
                        document.addField("NAME", name);
                    }

                    document.addField("PARENT", parentId);
                    document.addField("ASSOCTYPEQNAME", assocTypeQname);
                    document.addField("QNAME", map(schema, p.getName(), true));

                    if (p.isHard() && !isPrimaryParentProcessed.get()) {
                        document.addField("PRIMARYPARENT", parentId);
                        document.addField("PRIMARYASSOCTYPEQNAME", assocTypeQname);

                        isPrimaryParentProcessed.set(true);
                    }
                });
            } // endif has parents

            if (node.getPaths().isEmpty()) {
                if (StringUtils.equals(node.getTypeName(), "sys:store_root")) {
                    document.addField("PATH", "/");
                }

                document.addField("NODEPATH", ":" + node.getId() + ":");
            } else {
                // paths
                node.getPaths().forEach(p -> {
                    String[] s = p.getFilePath().split("/");
                    List<String> elements = Arrays.stream(s)
                        .filter(StringUtils::isNotBlank)
                        .map(x -> map(schema, x, true))
                        .collect(Collectors.toList());

                    document.addField("PATH", "/" + String.join("/", elements));
                    document.addField("PARENTPATH", "/" + String.join("/", elements.subList(0, elements.size() - 1)));
                    document.addField("NODEPATH", p.getPath());
                });
            } // endif no paths
        } // endif path

        if (match(flags, SG_FLAG) || match(flags, METADATA_FLAG)) {

            var sgIDs = node.getPaths()
                .stream()
                .map(NodePath::getSgPath)
                .filter(Objects::nonNull)
                .flatMap(s -> Arrays.stream(s.split(":")))
                .filter(StringUtils::isNotBlank)
                .map(Long::parseLong)
                .map(sgMap::get)
                .filter(Objects::nonNull)
                .toList();

            if (!sgIDs.isEmpty()) {
                sgIDs.forEach(sgid -> document.addField("SG", String.valueOf(sgid)));
            } else if (node.getSecurityGroup() != null) {
                document.addField("SG", String.valueOf(node.getSecurityGroup().getId()));
            }

        } // endif sg

        if (update) {
            // add modifier
            addSetModifier(document, "SG");
            addSetModifier(document, "PARENT");
            addSetModifier(document, "ASSOCTYPEQNAME");
            addSetModifier(document, "PRIMARYPARENT");
            addSetModifier(document, "PRIMARYASSOCTYPEQNAME");
            addSetModifier(document, "PATH");
            addSetModifier(document, "PARENTPATH");
            addSetModifier(document, "TEXT");

            customPropNames.forEach(s -> addSetModifier(document, s));
        } // endif update

        return new ImmutableTriple<>(document, update, textAsyncRequired ? node : null);
    }

    private PropertyContainer mapProperty(ModelSchema schema, ActiveNode node, Map.Entry<String,Object> entry) {
        PropertyDescriptor pd = schema.getProperty(entry.getKey());
        if (pd == null) {
            log.warn("Unable to find property descriptor for {} in tenant {} node {}", entry.getKey(), node.getTenant(), node.getUuid());
            if (!includeUnknownProperties) {
                return null;
            }

            pd = new PropertyDescriptor();
            pd.setName(entry.getKey());
            pd.setType(TYPE_ANY);
            pd.setIndexed(true);
        }

        PropertyContainer pc = new PropertyContainer();
        pc.setDescriptor(pd);

        Object value = entry.getValue();
        if (value != null) {
            switch (pd.getType()) {
                case TYPE_DATE, TYPE_DATETIME:
                    if (value instanceof Collection<?> collection) {
                        value = collection.stream()
                            .map(v -> Optional.ofNullable(DateISO8601Utils.parseAsZonedDateTime(v)).map(d -> d.format(DateTimeFormatter.ISO_INSTANT)).orElse(null))
                            .collect(Collectors.toList());
                    } else {
                        value = Optional.ofNullable(DateISO8601Utils.parseAsZonedDateTime(value)).map(d -> d.format(DateTimeFormatter.ISO_INSTANT)).orElse(null);
                    }
                    break;

                default:
                    break;
            } // end switch
        } // endif not null

        pc.setValue(value);
        return pc;
    }

    private void addField(SolrInputDocument document, String name, Object value, boolean notTokenizedRequired, boolean additionalTokenizedRequired) {
        document.addField(name, value);
        if (notTokenizedRequired) {
            document.addField(name + additionalNotTokenizedFieldSuffix, value);
        }
        if (additionalTokenizedRequired) {
            document.addField(name + additionalTokenizedFieldSuffix, value);
        }
    }

    private void addSetModifier(SolrInputDocument document, String name) {
        SolrInputField f = document.get(name);
        if (f != null) {
            Map<String,Object> fieldModifier = new HashMap<>(1);
            fieldModifier.put("set", f.getValue());
            document.setField(name, fieldModifier);
        }
    }

    private Object field(Object value, String modifier) {
        if (modifier != null) {
            Map<String,Object> fieldModifier = new HashMap<>(1);
            fieldModifier.put(modifier, value);
            value = fieldModifier;
        }

        return value;
    }

    private Object field(Object value, boolean set) {
        return field(value, set ? "set" : null);
    }

    private String cachedText(ActiveNode node, ContentProperty cp) {
        var text = cp.getText();
        if (text != null) {
            log.trace("Found cached text for node {} property {}: {} characters", node.getId(), cp.getName(), text.length());
        }

        return text;
    }

    private Pair<String,String> fullText(ActiveNode node, ContentProperty cp) {
        try {
            log.debug("Extracting tika text from {}", cp.getContentUrl());
            var path = contentStoreManager.getPath(cp.getContentUrl());
            try (var stream = Files.newInputStream(path)) {
                var result = StringUtils.stripToEmpty(tika.getText(stream));
                log.debug("Extracted {} characters", result == null ? 0 : result.length());
                return new ImmutablePair<>(result, null);
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);

            Throwable throwable = e;
            String emsg = throwable.getMessage();
            while (emsg == null && throwable.getCause() != null) {
                throwable = throwable.getCause();
                emsg = throwable.getMessage();
            }

            log.warn("Tika is unable to process content {} mimetype {} (tenant: {} uuid: {}): {}", cp.getContentUrl(), cp.getMimetype(), node.getTenant(), node.getUuid(), emsg);
            return new ImmutablePair<>("", emsg);
        }
    }

    private String map(ModelSchema schema, String prefixedName, boolean isPathElement) {
        if (retroCompatibilityMode) {
            final PrefixedQName qname = PrefixedQName.valueOf(prefixedName);
            final String localPart = isPathElement ? ISO9075.encode(qname.getLocalPart()) : qname.getLocalPart();
            if (StringUtils.isNotBlank(qname.getNamespaceURI())) {
                CustomModelSchema model = schema.getNamespaceSchema(qname.getNamespaceURI());
                if (model != null) {
                    prefixedName = model.getNamespace(qname.getNamespaceURI())
                        .map(uri -> new QName(uri.toString(), localPart).toString())
                        .orElse(prefixedName);
                }
            } else {
                prefixedName = localPart;
            }
        }

        return prefixedName;
    }

    public void removeAllHavingTransaction(TenantRef tenantRef, String tx) throws SolrServerException, IOException {
        if (fakeIndexModeEnabled) {
            log.debug("I should remove all node having transaction {} in tenant {}", tx, tenantRef);
            return;
        }

        UpdateResponse response = client.deleteByQuery(collectionName(tenantRef), String.format("TX:\"%s\"", tx));
        if (response.getStatus() != 0) {
            String msg = String.format(
                "Solr returned status %d removing documents having transaction %s",
                response.getStatus(),
                tx
            );
            throw new SystemException(msg);
        }
    }

}
