package it.doqui.libra.librabl.infrastructure.adapters.output.solr.search;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.ports.out.SearchPort;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.policy.SortDefinition;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.Expirable;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadQueryException;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.LimitExceededException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.PathDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.AbstractSolrController;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class SolrSearchController extends AbstractSolrController implements SearchPort {

    @ConfigProperty(name = "solr.joinCollectionMode", defaultValue = "default")
    String joinCollectionMode;

    @ConfigProperty(name = "solr.sgCacheSize", defaultValue = "10000")
    int sgCacheSize;

    @ConfigProperty(name = "solr.sgCacheDurationSec", defaultValue = "3600")
    long sgCacheDurationSec;

    @ConfigProperty(name = "solr.defaultRowsLimit", defaultValue = "32767")
    int defaultRowsLimit;

    @ConfigProperty(name = "solr.maxRowsLimit", defaultValue = "65535")
    int maxRowsLimit;

    @ConfigProperty(name = "solr.sg.cache.ttl", defaultValue = "24h")
    Duration cacheTTL;

    @Inject
    ModelManagerPort modelManager;

    @Inject
    NodeUseCase nodeService;

    @Inject
    PathDAO pathDAO;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    SessionContext sessionContext;

    private Cache<String, Expirable<Set<String>>> sgCache;

    @PostConstruct
    protected void init() {
        super.init();
        sgCache = Caffeine.newBuilder()
                .maximumSize(sgCacheSize)
                .expireAfterWrite(cacheTTL)
                .recordStats()
                .build();
    }

    @Override
    public Paged<NodeItem> findNodes(String q, List<SortDefinition> sortFields, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, Pageable pageable) throws SearchEngineException, IOException {
        Paged<String> p = findNodes(q, sortFields, pageable);
        long t0 = System.currentTimeMillis();
        Paged<NodeItem> result = new Paged<>(
            p.getPage(),
            p.getSize(),
            p.getTotalElements(),
            p.getTotalPages(),
            nodeService.listNodeMetadata(p.getItems(), optionSet, filterPropertyNames, locale, QueryScope.SEARCH)
        );
        log.debug("Got {} metadata for {} nodes in {} millis", result.getItems().size(), p.getItems().size(), (System.currentTimeMillis() - t0));
        return result;
    }

    @Override
    public Paged<String> findNodes(String q, List<SortDefinition> sortFields, Pageable pageable) throws SearchEngineException, IOException {
        Paged<SolrDocument> solrDocumentPaged = findNodes(q,sortFields,pageable,Set.of("ID"));
        return solrDocumentPaged.map(this::getID);
    }

    private Paged<SolrDocument> findNodes(String q, List<SortDefinition> sortFields, Pageable pageable, Set<String> returnFields) throws SearchEngineException, IOException {
        if (pageable != null && (pageable.getPage() > maxRowsLimit)) {
            throw new LimitExceededException(String.format("Invalid page %d size %d", pageable.getPage(), pageable.getSize()));
        }

        log.trace("Input Query: {}", q);
        final var qc = new QueryConverter(modelManager.getContextModel(), additionalTokenizedFieldSuffix);
        qc.setNumericPathEnabled(sessionContext.getTenantData().map(TenantData::isNumericPathEnabled).orElse(false));
        qc.setPathConvert(filePath -> pathDAO.findNodePathByFilePath(filePath).orElseThrow(() -> new BadRequestException("Invalid path " + filePath)));
        qc.setUuidFromPath(filePath -> pathDAO.findNodeUUIDByFilePath(filePath).orElseThrow(() -> new BadRequestException("Invalid path " + filePath)));

        q = qc.convertQuery(q);
        log.debug("Converted Query: {}", q);

        String sort = "";
        if (sortFields != null && !sortFields.isEmpty()) {
            var model = modelManager.getContextModel();
            sort += sortFields.stream()
                .map(f -> {
                    var name = f.getFieldName();
                    if (!Strings.CS.endsWith(name, additionalNotTokenizedFieldSuffix)) {
                        if (Strings.CS.startsWith(name, "@")) {
                            name = name.substring(1);
                        }

                        var pd = model.getProperty(name);
                        if (pd != null && pd.isNotTokenizedFieldRequired()) {
                            return SortDefinition.builder().fieldName(name + additionalNotTokenizedFieldSuffix).ascending(f.isAscending()).build();
                        }
                    }

                    return f;
                })
                .map(f -> String.format("%s %s,", mapField(f.getFieldName()), f.isAscending() ? "asc" : "desc"))
                .collect(Collectors.joining());
        }
        sort += "DBID asc";

        boolean unlimited = false;
        if (pageable == null) {
            pageable = new Pageable();
            pageable.setPage(0);
            pageable.setSize(defaultRowsLimit);
            log.warn("No pageable specified, Using page 0 and size {} as default", defaultRowsLimit);
            unlimited = true;
        } else if (pageable.getSize() < 1) {
            pageable.setSize(defaultRowsLimit);
            log.warn("No page size specified. Using size {} as default", defaultRowsLimit);
            unlimited = true;
        }

        final int start = pageable.getPage() * pageable.getSize();
        final int rows = pageable.getSize();
        final String collectionName = collectionName(sessionContext.getUserContext().getTenantRef());
        final JoinMode joinMode;
        if (sessionContext.getUserContext().isAdmin()) {
           joinMode = JoinMode.NONE;
        } else {
            var joinCollectionMode = tenantRepository.findByIdOptional(sessionContext.getUserContext().getTenantRef().getName())
                .map(TenantSpace::getData)
                .map(TenantData::getSolrJoinCollectionMode)
                .orElse(this.joinCollectionMode);
            switch (joinCollectionMode) {
                case "cross": {
                    joinMode = JoinMode.CROSS;
                    break;
                }

                case "combined": {
                    joinMode = unlimited ? JoinMode.LOCAL : JoinMode.JOIN;
                    break;
                }

                case "manual": {
                    joinMode = JoinMode.TWOPHASE;
                    break;
                }

                default: {
                    joinMode = JoinMode.JOIN;
                    break;
                }
            }
        }

        Set<String> sgSet = null;
        var username = sessionContext.getUserContext().getAuthorityRef().toString();
        if (joinMode == JoinMode.TWOPHASE || joinMode == JoinMode.LOCAL) {
            var cacheFQ = sgCache.getIfPresent(username);
            if (cacheFQ != null && !cacheFQ.isExpired()) {
                sgSet = cacheFQ.getObject();
                log.trace("SG list found in cache for user {} having size {}", username, sgSet.size());
            }

            if (sgSet == null) {
                log.trace("Lookup SG list from solr for user {}", username);
                var params = new HashMap<String, String>();
                params.put("q", String.format("AUTHORITY:(%s %s)", username, String.join(" ", sessionContext.getUserContext().getGroupSet())));
                params.put("start", "0");
                params.put("rows", "" + Integer.MAX_VALUE);
                params.put("fl", "ID");

                sgSet = listDocuments(collectionName + "-sg", params).stream().map(this::getID).collect(Collectors.toSet());
                log.debug("Got {} SG documents for user {}", sgSet.size(), username);
                sgCache.put(username, new Expirable<>(sgSet, ZonedDateTime.now().plusSeconds(sgCacheDurationSec)));
            }
        }

        final String fq;
        final var params = new HashMap<String, String>();
        switch (joinMode) {
            case JOIN, CROSS: {
                fq = String.format("@cm\\:creator:%s OR @cm\\:owner:%s OR {!%s fromIndex=\"%s-sg\" from=\"ID\" to=\"SG\" v=\"AUTHORITY:(%s %s)\"}",
                    username,
                    username,
                    (joinMode == JoinMode.CROSS ? "join method=\"crossCollection\" " : "join"),
                    collectionName,
                    username,
                    String.join(" ", sessionContext.getUserContext().getGroupSet()));
                break;
            }

            case TWOPHASE: {
                fq = String.format("@cm\\:creator:%s OR @cm\\:owner:%s", username, username) + (sgSet.isEmpty() ? "" : " OR " + sgSet.stream().collect(Collectors.joining(" ", "SG:(", ")")));
                break;
            }

            case LOCAL: {
                fq = null;
                if (returnFields == null) {
                    returnFields = new HashSet<>();
                    returnFields.add("*");
                } else {
                    returnFields = new HashSet<>(returnFields);
                }

                returnFields.add("SG");
                returnFields.add("OWNED:or(gt(query($creator_param),0),gt(query($owner_param),0))");
                params.put("creator_param", String.format("@cm\\:creator:%s", username));
                params.put("owner_param", String.format("@cm\\:owner:%s", username));
                break;
            }

            default:
                fq = null;
                break;
        }

        log.debug("Calling solr with q: -> {} <- sorting by {} joining with {}", q, sort, joinMode);
        if (fq != null) {
            log.trace("Using fq {}", fq);
        }

        long t0 = System.currentTimeMillis();
        try {
            // Solr Query execution to solve solr header size limit
            JsonQueryRequest solrQuery = new JsonQueryRequest()
                .setQuery(q)
                .setOffset(start)
                .setLimit(rows)
                .setSort(sort);

            if (fq != null) {
                solrQuery = solrQuery.withFilter(fq);
            }

            if (returnFields != null) {
                solrQuery = solrQuery.returnFields(returnFields);
            }

            for (Map.Entry<String, String> entry : params.entrySet()) {
                solrQuery = solrQuery.withParam(entry.getKey(), entry.getValue());
            }

            final QueryResponse response = solrQuery.process(client, collectionName);
            //Previous mode of execute solr query:
            //final QueryResponse response = client.query(collectionName, queryParams);
            final SolrDocumentList documents = filter(response.getResults(), joinMode == JoinMode.LOCAL ? sgSet : null);
            log.debug("Found {} documents in {} millis", documents.getNumFound(), (System.currentTimeMillis() - t0));
            return new Paged<>(
                pageable.getPage(),
                pageable.getSize(),
                documents.getNumFound(),
                pageable.getSize() > 0
                    ? (documents.getNumFound() / pageable.getSize() + (documents.getNumFound() % pageable.getSize() == 0 ? 0 : 1))
                    : 1,
                documents);
        } catch (BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            log.error("Got solr exception from {}: {}", e.getClass().getSimpleName(), e.getMessage());
            if (Strings.CS.contains(e.getMessage(), "undefined field")) {
                throw new BadQueryException(e.getMessage());
            }

            throw new SearchEngineException(e);
        }
    }

    private SolrDocumentList filter(SolrDocumentList documents, Set<String> sgSet) {
        if (sgSet == null) {
            return documents;
        }

        var filteredDocuments = new SolrDocumentList();
        for (SolrDocument document : documents) {
            if (document.getFirstValue("OWNED") instanceof Boolean b && b) {
                filteredDocuments.add(document);
            }

            if (document.getFieldValues("SG").stream().filter(Objects::nonNull).map(Object::toString).anyMatch(sgSet::contains)) {
                filteredDocuments.add(document);
            }
        }

        filteredDocuments.setNumFound(documents.size());
        return filteredDocuments;
    }

    private String mapField(String name) {
        if (Strings.CS.startsWith(name, "@")) {
            name = name.substring(1);
        }

        PrefixedQName qname = PrefixedQName.valueOf(name);
        if (StringUtils.isBlank(qname.getNamespaceURI())) {
            return qname.getLocalPart();
        }

        return "@" + name;
    }

    private enum JoinMode {
        JOIN,
        CROSS,
        LOCAL,
        TWOPHASE,
        NONE
    }

}
