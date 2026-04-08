package it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.agroal.api.AgroalDataSource;
import io.quarkus.tika.TikaParser;
import it.doqui.libra.librabl.application.model.configuration.AsyncConfig;
import it.doqui.libra.librabl.application.model.messaging.MessageType;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import it.doqui.libra.librabl.domain.model.graph.IndexingFlags;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.model.schema.PropertyContainer;
import it.doqui.libra.librabl.domain.model.schema.PropertyDescriptor;
import it.doqui.libra.librabl.domain.model.schema.TypedInterfaceDescriptor;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.IndexerDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.ReindexTable;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.AbstractSolrController;
import it.doqui.libra.librabl.infrastructure.platform.stats.StatController;
import it.doqui.libra.librabl.infrastructure.platform.stats.StatMeasure;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.TriConsumer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.application.model.messaging.MessagePriority.VERYHIGH_PRIORITY;
import static it.doqui.libra.librabl.domain.model.graph.Constants.*;
import static it.doqui.libra.librabl.domain.model.graph.IndexingFlags.*;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.*;

@ApplicationScoped
@Slf4j
public class Indexer extends AbstractSolrController {

    @ConfigProperty(name = "solr.fullTextSizeThreshold", defaultValue = "0")
    long fullTextSizeThreshold;

    @ConfigProperty(name = "solr.indexer.pagination.addNodesPageSize", defaultValue = "50")
    int addNodesPageSize;

    @ConfigProperty(name = "solr.indexer.pagination.removeNodesPageSize", defaultValue = "100")
    int removeNodesPageSize;

    @ConfigProperty(name = "solr.indexer.includeUnknownProperties", defaultValue = "false")
    boolean includeUnknownProperties;

    @ConfigProperty(name = "libra.reindex.queue", defaultValue = "tasks")
    String queueName;

    @ConfigProperty(name = "libra.reindex.time-before-clean", defaultValue = "168h")
    Duration timeBeforeClean;

    @ConfigProperty(name = "libra.content-store.tenant-isolation", defaultValue = "true")
    boolean globalTenantIsolationEnabled;

    @Inject
    ModelManagerPort modelManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ContentRepository contentStoreManager;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @Inject
    IndexerDAO dao;

    @Inject
    MessageSenderPort producer;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    TikaParser tika;

    @Inject
    StatController statController;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    SessionContext sessionContext;

    public void reindexTransactions(String tenant, String dbSchema, Collection<Long> txIds, int flags, Set<String> includedUUIDs, Set<String> excludedUUIDs, boolean async, boolean addOnly) {
        var tasks = DBUtils.transactionCall(ds, Optional.ofNullable(dbSchema).orElse(sessionContext.getUserContext().getDbSchema()),
                conn -> reindexTransactions(conn, tenant, txIds, flags, includedUUIDs, excludedUUIDs, async, addOnly)
        );

        tasks.forEach(producer::submit);
    }

    public List<ReindexTask> reindexTransactions(Connection conn, String tenant, Collection<Long> txIds, int flags, Set<String> includedUUIDs, Set<String> excludedUUIDs, boolean async, boolean addOnly) {
        final StatMeasure sm = new StatMeasure();
        sm.step("total", System.currentTimeMillis());
        try {
            log.info("Indexing transactions {} in schema {} and tenant {} (mode: {})", txIds, conn.getSchema(), tenant, async ? "ASYNC" : "SYNC");
            final Set<Long> txSet = new HashSet<>();
            final Set<Long> txFailedSet = new HashSet<>();
            final List<ReindexTask> textAsyncTasks = new LinkedList<>();
            var tenantIsolationEnabled = tenantRepository.findByIdOptional(tenant)
                    .map(TenantSpace::getData)
                    .map(TenantData::getTenantIsolationEnabled)
                    .orElse(globalTenantIsolationEnabled);
            var registerFullTextSQL = "update ecm_files set cached_text = ?, error_msg = ? where contentref = ?";
            if (tenantIsolationEnabled) {
                registerFullTextSQL += " and tenant = ?";
            }

            try (var stmt = conn.prepareStatement(registerFullTextSQL)) {

                String collection = collectionName(TenantRef.valueOf(tenant));

                Collection<ActiveNode> nodes;
                List<String> uuids;
                Collection<SecurityGroup> sgs;

                var ephemeralUUIDs = new LinkedList<String>();
                var deletedIDs = new LinkedList<Long>();
                boolean indexingDisabled = tenantRepository.findByIdOptional(tenant)
                    .map(TenantSpace::getData)
                    .map(TenantData::isIndexingDisabled)
                    .orElse(sessionContext.getTenantData().map(TenantData::isIndexingDisabled).orElse(false));

                // reindex active nodes + tn nodes + paths
                final Pageable pageable = new Pageable();
                for (var reindexTable : ReindexTable.values()) {
                    if (reindexTable.equals(ReindexTable.ARC_NODES)) {
                        continue;
                    }

                    pageable.setSize(addNodesPageSize);
                    pageable.setPage(0);

                    int foundNodeCount;
                    do {
                        long t0 = System.currentTimeMillis();
                        var filteredNodeSet = dao.findNodesWithTx(conn, txIds, includedUUIDs, excludedUUIDs, pageable, indexingDisabled, reindexTable);
                        nodes = filteredNodeSet.filteredNodes();
                        foundNodeCount = filteredNodeSet.totalNodeCount();
                        log.debug("Found nodes {} from table {} using {}", nodes.stream().map(ActiveNode::getId).toList(), reindexTable.mapToTableName(false), pageable);
                        nodes.stream().filter(node -> node.getAspects().contains(ASPECT_ECMSYS_EPHEMERAL)).map(ActiveNode::getUuid).forEach(ephemeralUUIDs::add);
                        nodes.stream().filter(node -> node.getAspects().contains(ASPECT_ECMSYS_DELETED)).map(ActiveNode::getId).forEach(deletedIDs::add);
                        var sgMap = dao.mapSecurityGroups(conn, nodes);
                        sm.add("dbread", System.currentTimeMillis() - t0);
                        var counter = new AtomicInteger(0);
                        var r = reindex(tenant, collection, nodes, sgMap, flags, async, sm, (contentUrl, text, emsg) -> {
                            try {
                                int c = 0;
                                stmt.setString(++c, text);
                                stmt.setString(++c, emsg == null ? null : (emsg.length() > 255 ? emsg.substring(0, 255) : emsg));
                                stmt.setString(++c, contentUrl);
                                if (tenantIsolationEnabled) {
                                    stmt.setString(++c, tenant);
                                }

                                stmt.addBatch();
                                counter.incrementAndGet();
                            } catch (SQLException e) {
                                throw new SystemException(e);
                            }
                        });

                        nodes.stream().map(ActiveNode::getTx).map(ApplicationTransaction::getId).filter(tx -> r.failedTXs().contains(tx)).forEach(txFailedSet::add);
                        nodes.stream().map(ActiveNode::getTx).map(ApplicationTransaction::getId).filter(tx -> !r.failedTXs().contains(tx)).forEach(txSet::add);
                        textAsyncTasks.addAll(r.reindexTasks());
                        r.indexedNodes().forEach((id) -> log.info("Node ID {} indexed (tenant {})", id, tenant));

                        if (counter.get() > 0) {
                            stmt.executeBatch();
                        }

                        pageable.setPage(pageable.getPage() + 1);
                    } while (foundNodeCount >= pageable.getSize());
                } // end for table

                // indexing SGs
                {
                    long t0 = System.currentTimeMillis();
                    sgs = dao.findSGWithTx(conn, txIds, tenant, null);
                    sgs.stream().map(SecurityGroup::getTx).map(ApplicationTransaction::getId).filter(tx -> !txFailedSet.contains(tx)).forEach(txSet::add);
                    sm.add("dbread", System.currentTimeMillis() - t0);
                    log.debug("Found SGs: {} (tenant {})", sgs.stream().map(SecurityGroup::getId).toList(), tenant);
                    var _sgs = sgs.stream().filter(sg -> !sg.getRules().isEmpty()).toList();
                    var _emptySGs = sgs.stream().filter(sg -> sg.getRules().isEmpty()).toList();
                    var indexedSGs = reindexSGs(collection + "-sg", _sgs, sm);
                    indexedSGs.forEach((id) -> log.info("SG {} indexed (tenant {})", id, tenant));
                    removeAll(collection + "-sg", _emptySGs.stream().map(sg -> "" + sg.getId()).toList(), sm);
                    _emptySGs.forEach((sg) -> log.info("SG {} unindexed (tenant {})", sg.getId(), tenant));
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
                        var deletedNodeRefs = dao.findArchivedUUIDsWithTx(conn, txIds, pageable);
                        sm.add("dbread", System.currentTimeMillis() - t0);
                        uuids = deletedNodeRefs.stream().map(IndexerDAO.DeletedNodeRef::uuid).toList();
                        deletedNodeRefs.stream().map(IndexerDAO.DeletedNodeRef::txId).forEach(txSet::add);
                        removeAll(collection, uuids, sm);
                        pageable.setPage(pageable.getPage() + 1);
                    } while (uuids.size() >= pageable.getSize());

                    pageable.setPage(0);
                    // remove removed/purged nodes from solr
                    do {
                        long t0 = System.currentTimeMillis();
                        var deletedNodeRefs = dao.findRemovedUUIDsWithTx(conn, txIds, pageable);
                        sm.add("dbread", System.currentTimeMillis() - t0);
                        uuids = deletedNodeRefs.stream().map(IndexerDAO.DeletedNodeRef::uuid).toList();
                        deletedNodeRefs.stream().map(IndexerDAO.DeletedNodeRef::txId).forEach(txSet::add);
                        removeAll(collection, uuids, sm);
                        cleanNodes(tenant, uuids, sm);
                        pageable.setPage(pageable.getPage() + 1);
                    } while (uuids.size() >= pageable.getSize());
                }

                client.commit(collection);
                client.commit(collection + "-sg");

                // register tx as indexed
                long t0 = System.currentTimeMillis();
                if (!deletedIDs.isEmpty()) {
                    dao.removeDeletedNodes(conn, deletedIDs);
                }

                dao.setTransactionIndexedNow(conn, txSet, null);
                if (!txFailedSet.isEmpty()) {
                    dao.setTransactionIndexedNow(conn, txFailedSet, true);
                }

                var txEmpties = txIds.stream().filter(tx -> !txSet.contains(tx)).filter(tx -> !txFailedSet.contains(tx)).toList();
                if (!txEmpties.isEmpty()) {
                    dao.setTransactionIndexedNow(conn, txEmpties, false);
                }

                sm.add("settx", System.currentTimeMillis() - t0);
                txSet.forEach((txID) -> log.info("TX ID {} indexed (tenant {})", txID, tenant));
                txFailedSet.forEach((txID) -> log.warn("TX ID {} indexed with warning (tenant {})", txID, tenant));
                txEmpties.forEach((txID) -> log.info("TX ID {} indexed resulting empty (tenant {})", txID, tenant));
            }

            return textAsyncTasks;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new SystemException(e);
        } finally {
            sm.step("total", System.currentTimeMillis());
            sm.add("tx", txIds.size());

            statController.add(sm);
        }
    }

    private Collection<Long> reindexSGs(String collection, Collection<SecurityGroup> sgs, final StatMeasure sm) {
        if (sgs == null || sgs.isEmpty()) {
            return List.of();
        }

        log.debug("Indexing {} SGs into collection {}", sgs.size(), collection);
        final List<SolrInputDocument> documents = sgs.stream()
            .map(sg -> {
                SolrInputDocument document = new SolrInputDocument();
                document.addField("ID", String.valueOf(sg.getId()));
                document.addField("TX", String.valueOf(sg.getTx().getId()));
                document.addField("TSTAMP", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));

                sg.getRules().stream()
                    .filter(ar -> Strings.CS.startsWith(ar.getRights(), "1"))
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

        return documents.stream().map(this::getID).map(Long::parseLong).toList();
    }

    private ReindexResult reindex(String tenant, String collection, Collection<ActiveNode> nodes, final Map<Long,Long> sgMap, int flags, boolean async, final StatMeasure sm, final TriConsumer<String,String,String> fullTextConsumer) {
        if (nodes == null || nodes.isEmpty()) {
            return new ReindexResult(List.of(), List.of(), List.of());
        }

        final ModelSchema schema = modelManager.getContextModel();
        final Multimap<Long,String> textAsyncNodeMap = ArrayListMultimap.create();
        final List<SolrInputDocument> documents = new ArrayList<>(nodes.size());
        final Set<Long> failedTXs = new HashSet<>();
        nodes.stream()
            .filter(node -> !node.getAspects().contains(ASPECT_ECMSYS_EPHEMERAL))
            .filter(node -> !node.getAspects().contains(ASPECT_ECMSYS_DELETED))
            .map(node -> {
                try {
                    return createDocument(schema, node, sgMap, flags, async, sm, fullTextConsumer);
                } catch (BadDataException e) {
                    failedTXs.add(node.getTx().getId());
                    log.warn("Unable to index tenant {} node {}. Got {}", node.getTenant(), node.getUuid(), e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(x -> {
                ActiveNode r = x.node();
                if (r != null && x.textAsyncRequired) {
                    textAsyncNodeMap.put(r.getTx().getId(), r.getUuid());
                }

                SolrInputDocument document = x.document();
                if (document == null) {
                    Optional.ofNullable(r).map(ActiveNode::getTx).map(ApplicationTransaction::getId).ifPresent(tx -> {
                        failedTXs.add(tx);
                        log.warn("Registering warning for tx {}: missing document for node {}", tx, r.getId());
                    });
                    return;
                }

                if (x.hasWarnings()) {
                    Optional.ofNullable(r).map(ActiveNode::getTx).map(ApplicationTransaction::getId).ifPresent(tx -> {
                        failedTXs.add(tx);
                        log.warn("Registering warning for tx {} reported by node {}", tx, r.getId());
                    });
                }

                boolean update = x.updateMode();
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

        List<ReindexTask> reindexTasks = new ArrayList<>();
        if (!textAsyncNodeMap.isEmpty()) {
            for (Long txId : textAsyncNodeMap.keySet()) {
                assert txId != null;
                ReindexTask m = new ReindexTask();
                m.setTaskId(UuidCreator.getTimeOrderedEpoch().toString());
                m.setFlags(TEXT_FLAG);
                m.setTenant(tenant);
                m.setTx(txId);
                m.setIncludeSet(new HashSet<>(textAsyncNodeMap.get(txId)));
                m.setCompleted(false);
                m.setPriority(VERYHIGH_PRIORITY);
                m.setQueueName(queueName);
                reindexTasks.add(m);
            }
        }

        return new ReindexResult(reindexTasks, documents.stream().map(this::getDBID).filter(Objects::nonNull).toList(), failedTXs);
    }

    private record ReindexResult(List<ReindexTask> reindexTasks, Collection<Long> indexedNodes, Collection<Long> failedTXs) { }

    private void cleanNodes(String tenant, List<String> uuids, final StatMeasure sm) {
        // schedule clean of uuids at deltat > tempo di backup in config
        long t0 = System.currentTimeMillis();
        submitNodeClean(tenant, uuids, timeBeforeClean.toMillis());
        sm.add("cleaner", System.currentTimeMillis() - t0);
    }

    private void submitNodeClean(String tenant, List<String> uuids, long delay) {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }

        producer.submit(
                MessageType.NODES_CLEAN,
                Map.of("tenant", tenant, "uuids", String.join(",", uuids)),
                0,
                delay,
                asyncConfig.expirables().queue());
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
            id = String.valueOf(f.getFirstValue());
        }

        return id;
    }

    private Long getDBID(SolrInputDocument document) {
        return Optional.ofNullable(document.get("DBID"))
            .map(SolrInputField::getFirstValue)
            .map(Object::toString)
            .map(Long::parseLong)
            .orElse(null);
    }

    private record SolrDocumentStatement(SolrInputDocument document, ActiveNode node, boolean updateMode, boolean textAsyncRequired, boolean hasWarnings) {}

    private SolrDocumentStatement createDocument(ModelSchema schema, ActiveNode node, Map<Long,Long> sgMap, int flags, boolean async, final StatMeasure sm, final TriConsumer<String,String,String> fullTextConsumer) {
        log.debug("Creating solr document for node {}", node.getId());
        sm.add("addedNodes", 1);
        // Per il momento viene forzato update completo del doc grazie a FULL_FLAG_MASK
        flags = IndexingFlags.combine(FULL_FLAG_MASK, flags, IndexingFlags.parse(node.getTransactionFlags()));
        final SolrInputDocument document = new SolrInputDocument();

        boolean update = !match(flags, METADATA_FLAG);
        boolean hasWarnings = false;
        boolean textAsyncRequired = false;
        final Set<String> customPropNames = new HashSet<>();

        document.addField("ID", node.getUuid());
        document.addField("TX", field(String.valueOf(node.getTx().getId()), update));
        document.addField("TSTAMP", field(ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT), update));
        if (match(flags, METADATA_FLAG)) {
            document.addField("DBID", node.getId());

            // aspects
            node.getData().getAspects()
                .forEach(a -> document.addField("ASPECT", a));

            // contents
            node.getData().getContents()
                .forEach(cp -> node.getData().getProperties().compute(cp.getName(), (k, v) -> {
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
                }));

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
                    String name = "@" + pc.getDescriptor().getName();
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
            document.addField("EXACTTYPE", node.getTypeName());
            try {
                var types = schema.getTypeHierarchy(node.getTypeName()).stream().map(TypedInterfaceDescriptor::getName).toList();
                types.forEach(name -> document.addField("TYPE", name));
            } catch (BadDataException e) {
                document.addField("TYPE", node.getTypeName());
                document.addField("ASPECT", "ecm-sys:invalid");
                hasWarnings = true;
            }
        } // endif metadata

        if (match(flags, TEXT_FLAG)
            && !node.getData().getAspects().contains(ASPECT_ECMSYS_DISABLED_FULLTEXT)
            && !node.getData().getAspects().contains(ASPECT_ECMSYS_ENCRYPTED)
            && !sessionContext.getTenantData().map(TenantData::isFullTextDisabled).orElse(false)) {
            // contents
            for (var cp : node.getData().getContents()) {
                long size = Optional.ofNullable(cp.getSize()).orElse(0L);
                if (!cp.isOpaque() && cp.getContentUrl() != null && size > 0 && (fullTextSizeThreshold <= 0 || size < fullTextSizeThreshold)) {
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
                    var parentId = p.getParent().getUuid();
                    var assocTypeQname = p.getTypeName();
                    var name = StringUtils.stripToEmpty(PrefixedQName.valueOf(p.getName()).getLocalPart());
                    if (StringUtils.isNotBlank(name)) {
                        document.addField("NAME", name);
                    }

                    document.addField("PARENT", parentId);
                    document.addField("ASSOCTYPEQNAME", assocTypeQname);
                    document.addField("QNAME", p.getName());

                    if (p.isHard() && !isPrimaryParentProcessed.get()) {
                        document.addField("PRIMARYPARENT", parentId);
                        document.addField("PRIMARYASSOCTYPEQNAME", assocTypeQname);

                        isPrimaryParentProcessed.set(true);
                    }
                });
            } // endif has parents

            if (node.getPaths().isEmpty()) {
                if (Strings.CS.equals(node.getTypeName(), "sys:store_root")) {
                    // solo root ha path / gli zombie rimangono senza path
                    document.addField("PATH", "/");
                }

                document.addField("NODEPATH", ":" + node.getId() + ":");
            } else {
                // paths
                if (node.getPaths().stream().anyMatch(NodePath::isHard)) {
                    node.getPaths().forEach(p -> {
                        String[] s = p.getFilePath().split("/");
                        List<String> elements = Arrays.stream(s)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toList());

                        if (elements.isEmpty()) {
                            document.addField("PATH", "/");
                            document.addField("NODEPATH", ":" + node.getId() + ":");
                        } else {
                            document.addField("PATH", "/" + String.join("/", elements));
                            document.addField("PARENTPATH", "/" + String.join("/", elements.subList(0, elements.size() - 1)));
                            document.addField("NODEPATH", p.getPath());
                        }
                    });
                } else {
                    // zombie raggiungibili solo da soft link
                    node.getPaths().forEach(p -> document.addField("NODEPATH", p.getPath()));
                }

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

        log.debug("Node {} prepared (requires reindex: {}, has warning: {})", node.getId(), textAsyncRequired, hasWarnings);
        return new SolrDocumentStatement(document, node, update, textAsyncRequired, hasWarnings);
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

    private String cachedText(ActiveNode node, FileData cp) {
        var text = cp.getText();
        if (text != null) {
            log.trace("Found cached text for node {} property {}: {} characters", node.getId(), cp.getName(), text.length());
        }

        return text;
    }

    private Pair<String,String> fullText(ActiveNode node, FileData cp) {
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
