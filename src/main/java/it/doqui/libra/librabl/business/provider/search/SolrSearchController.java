package it.doqui.libra.librabl.business.provider.search;

import it.doqui.libra.librabl.business.provider.data.dao.PathDAO;
import it.doqui.libra.librabl.business.provider.integration.solr.AbstractSolrController;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.exceptions.SearchEngineException;
import it.doqui.libra.librabl.business.service.interfaces.NodeService;
import it.doqui.libra.librabl.business.service.interfaces.SearchService;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.business.service.node.SortDefinition;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.cache.LRUCache;
import it.doqui.libra.librabl.foundation.Expirable;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadQueryException;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.LimitExceededException;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.node.NodeItem;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.MapSolrParams;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class SolrSearchController extends AbstractSolrController implements SearchService {

    @ConfigProperty(name = "solr.joinCollectionMode", defaultValue = "default")
    String joinCollectionMode;

    @ConfigProperty(name = "solr.sgCacheSize", defaultValue = "10000")
    int sgCacheSize;

    @ConfigProperty(name = "solr.defaultRowsLimit", defaultValue = "10")
    int defaultRowsLimit;

    @ConfigProperty(name = "solr.maxRowsLimit", defaultValue = "65535")
    int maxRowsLimit;

    @Inject
    ModelManager modelManager;

    @Inject
    NodeService nodeService;

    @Inject
    PathDAO pathDAO;

    private LRUCache<String, Expirable<String>> sgCache;

    @PostConstruct
    protected void init() {
        super.init();
        sgCache = new LRUCache<>(sgCacheSize);
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
        Paged<SolrDocument> solrDocumentPaged = findNodes(q,sortFields,pageable,List.of("ID"));
        return solrDocumentPaged.map(this::getID);
    }

    @Override
    public Paged<SolrDocument> findNodes(String q, List<SortDefinition> sortFields, Pageable pageable, List<String> returnFields) throws SearchEngineException, IOException {
        if (pageable != null && (pageable.getPage() > maxRowsLimit)) {
            throw new LimitExceededException(String.format("Invalid page %d size %d", pageable.getPage(), pageable.getSize()));
        }

        log.trace("Input Query: {}", q);
        final var qc = new QueryConverter(UserContextManager.getContext().getTenantRef(), modelManager.getContextModel(), additionalTokenizedFieldSuffix);
        qc.setRetroCompatibilityMode(retroCompatibilityMode);
        qc.setNumericPathEnabled(UserContextManager.getTenantData().map(TenantData::isNumericPathEnabled).orElse(false));
        qc.setPathConvert(filePath -> pathDAO.findNodePathByFilePath(filePath).orElseThrow(() -> new BadRequestException("Invalid path " + filePath)));
        qc.setUuidFromPath(filePath -> pathDAO.findNodeUUIDByFilePath(filePath).orElseThrow(() -> new BadRequestException("Invalid path " + filePath)));

        q = qc.convertQuery(q);
        log.debug("Converted Query: {}", q);

        final Map<String, String> queryParamMap = new HashMap<>();
        queryParamMap.put("q", q);

        if (returnFields != null) {
            queryParamMap.put("fl", String.join(",", returnFields));
        }

        String sort = "";
        if (sortFields != null && !sortFields.isEmpty()) {
            var model = modelManager.getContextModel();
            sort += sortFields.stream()
                .map(f -> {
                    var name = f.getFieldName();
                    if (!StringUtils.endsWith(name, additionalNotTokenizedFieldSuffix)) {
                        if (StringUtils.startsWith(name, "@")) {
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
        queryParamMap.put("sort", sort);
        log.debug("Sorting by {}", sort);

        if (pageable == null) {
            pageable = new Pageable();
            pageable.setPage(0);
            pageable.setSize(defaultRowsLimit);
            log.warn("No pageable specified, Using page 0 and size {} as default", defaultRowsLimit);
        } else if (pageable.getSize() < 1) {
            pageable.setSize(defaultRowsLimit);
            log.warn("No page size specified. Using size {} as default", defaultRowsLimit);
        }

        queryParamMap.put("start", String.valueOf(pageable.getPage() * pageable.getSize()));
        queryParamMap.put("rows", String.valueOf(pageable.getSize()));

        final String collectionName = collectionName(UserContextManager.getContext().getTenantRef());
        if (!UserContextManager.getContext().isAdmin()) {
            var username = UserContextManager.getContext().getAuthorityRef().toString();
            final String fq;
            switch (joinCollectionMode) {
                case "default": {
                    fq = String.format("@cm\\:creator:%s OR @cm\\:owner:%s OR {!join fromIndex=\"%s-sg\" from=\"ID\" to=\"SG\" v=\"AUTHORITY:(%s %s)\"}",
                        username,
                        username,
                        collectionName,
                        username,
                        String.join(" ", UserContextManager.getContext().getGroupSet()));
                    break;
                }
                case "cross": {
                    fq = String.format("{!join method=\"crossCollection\" fromIndex=\"%s-sg\" from=\"ID\" to=\"SG\" v=\"AUTHORITY:(%s %s)\"}",
                        collectionName,
                        username,
                        String.join(" ", UserContextManager.getContext().getGroupSet()));
                    break;
                }

                case "multiple": {
                    var conditions = new ArrayList<String>();
                    conditions.add(String.format("{!join method=\"crossCollection\" fromIndex=\"%s-sg\" from=\"ID\" to=\"SG\" v=\"AUTHORITY:(%s)\"}", collectionName, username));
                    for (String group : UserContextManager.getContext().getGroupSet()) {
                        conditions.add(String.format("{!join method=\"crossCollection\" fromIndex=\"%s-sg\" from=\"ID\" to=\"SG\" v=\"AUTHORITY:(%s)\"}", collectionName, group));
                    }

                    fq = conditions.stream().collect(Collectors.joining(" OR ", "(", ")"));
                    break;
                }

                case "none": {
                    var cacheFQ = sgCache.get(username);
                    if (cacheFQ.isPresent()) {
                        var cachedItem = cacheFQ.get();
                        if (!cachedItem.isExpired()) {
                            log.trace("SG list found in cache for user {}", username);
                            fq = cachedItem.getObject();
                            break;
                        }
                    }

                    log.trace("Lookup SG list from solr for user {}", username);
                    var params = new HashMap<String, String>();
                    params.put("q", String.format("AUTHORITY:(%s %s)", username, String.join(" ", UserContextManager.getContext().getGroupSet())));
                    params.put("start", "0");
                    params.put("rows", "" + Integer.MAX_VALUE);
                    params.put("fl", "ID");
                    fq = listDocuments(collectionName + "-sg", params)
                        .stream()
                        .map(this::getID)
                        .collect(Collectors.joining(" ", "SG:(", ")"));

                    sgCache.put(username, new Expirable<>(fq, ZonedDateTime.now().plusSeconds(60)));
                    break;
                }

                default:
                    fq = null;
                    break;
            }

            if (fq != null) {
                queryParamMap.put("fq", fq);
            }

        }

        log.debug("Calling solr with params {}", queryParamMap);
        final MapSolrParams queryParams = new MapSolrParams(queryParamMap);
        long t0 = System.currentTimeMillis();

        try {
            // Solr Query execution to solve solr header size limit
            JsonQueryRequest solrQuery = new JsonQueryRequest()
                .setQuery(queryParams.get("q"))
                .setOffset(queryParams.getInt("start", 0))
                .setLimit(queryParams.getInt("rows", defaultRowsLimit))
                .setSort(queryParams.get("sort", "DBID asc"));

            if (queryParams.get("fq") != null) {
                solrQuery = solrQuery.withFilter(queryParams.get("fq"));
            }

            if (queryParams.get("fl") != null) {
                solrQuery = solrQuery.returnFields(returnFields);
            }

            final QueryResponse response = solrQuery.process(client, collectionName);
            //Previous mode of execute solr query: final QueryResponse response = client.query(collectionName, queryParams);
            final SolrDocumentList documents = response.getResults();

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
            if (StringUtils.contains(e.getMessage(), "undefined field")) {
                throw new BadQueryException(e.getMessage());
            }

            throw new SearchEngineException(e);
        }
    }

    private String mapField(String name) {
        if (StringUtils.startsWith(name, "@")) {
            name = name.substring(1);
        }

        PrefixedQName qname = PrefixedQName.valueOf(name);
        if (StringUtils.isBlank(qname.getNamespaceURI())) {
            return qname.getLocalPart();
        }

        final String result;
        if (retroCompatibilityMode) {
            ModelSchema schema = modelManager.getContextModel();
            CustomModelSchema customModelSchema = schema.getNamespaceSchema(qname.getNamespaceURI());
            result = customModelSchema.getNamespace(qname.getNamespaceURI())
                .map(URI::toString)
                .map(s -> new QName(s, qname.getLocalPart()).toString())
                .orElse(name);
        } else {
            result = name;
        }

        return "@" + result;
    }

}
