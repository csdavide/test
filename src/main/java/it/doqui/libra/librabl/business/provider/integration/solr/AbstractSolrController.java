package it.doqui.libra.librabl.business.provider.integration.solr;

import it.doqui.libra.librabl.business.service.exceptions.SearchEngineException;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadQueryException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.MapSolrParams;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.WORKSPACE;

@Slf4j
public abstract class AbstractSolrController {

    @ConfigProperty(name = "solr.endpoint")
    String endpoint;

    @ConfigProperty(name = "solr.username")
    Optional<String> username;

    @ConfigProperty(name = "solr.password")
    Optional<String> password;

    @ConfigProperty(name = "solr.collectionPrefix", defaultValue = "")
    Optional<String> prefix;

    @ConfigProperty(name = "solr.cloudMode", defaultValue = "false")
    boolean useCloudMode;

    @ConfigProperty(name = "solr.checkAliveInterval", defaultValue = "60s")
    Duration checkAliveInterval;

    @ConfigProperty(name = "solr.retroCompatibilityMode", defaultValue = "false")
    protected boolean retroCompatibilityMode;

    @ConfigProperty(name = "solr.fakeIndexModeEnabled", defaultValue = "false")
    protected boolean fakeIndexModeEnabled;

    @ConfigProperty(name = "solr.additionalNotTokenizedFieldSuffix", defaultValue = "_nt")
    protected String additionalNotTokenizedFieldSuffix;

    @ConfigProperty(name = "solr.additionalTokenizedFieldSuffix", defaultValue = "_locale_it")
    protected String additionalTokenizedFieldSuffix;

    protected SolrClient client = null;

    @PostConstruct
    protected void init() {
        var endpoints = Arrays.stream(endpoint.split(","))
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());

        if (endpoints.isEmpty()) {
            throw new RuntimeException("Invalid solr endpoint");
        }

        Http2SolrClient.Builder builder;
        if (endpoints.size() > 1) {
            builder = new Http2SolrClient.Builder();
        } else {
            builder = new Http2SolrClient.Builder(endpoints.get(0));
        }

        if (username.isPresent() && password.isPresent()) {
            builder = builder.withBasicAuthCredentials(username.get(),password.get());
        }

        if (endpoints.size() > 1) {
            if (useCloudMode) {
                client = new CloudHttp2SolrClient
                    .Builder(endpoints)
                    .withHttpClient(builder.build())
                    .build();
                log.info("Using Cloud Solr Client");
            } else {
                client = new LBHttp2SolrClient
                    .Builder(builder.build(), endpoints.toArray(endpoints.toArray(new String[0])))
                    .setAliveCheckInterval((int) checkAliveInterval.toSeconds(), TimeUnit.SECONDS)
                    .build();
                log.info("Using Load Balanced Solr Client");
            }
        } else {
            client = builder.build();
            log.info("Using Http Solr Client");
        }
    }

    protected String collectionName(TenantRef tenantRef) {
        if (retroCompatibilityMode) {
            return String.format("primary_%s_%s-%s", WORKSPACE, tenantRef.getName(), "SpacesStore").toLowerCase();
        } else {
            var name = tenantRef.toString().replace("::", "_").toLowerCase();
            return prefix.isEmpty() || StringUtils.isBlank(prefix.get()) ? name : prefix.get() + "-" + name;
        }
    }

    protected String getID(SolrDocument document) {
        String id = (String) document.getFirstValue("ID");
        if (retroCompatibilityMode) {
            try {
                URI uri = new URI(id);
                id = uri.getPath().substring(1);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        return id;
    }

    protected String extractID(String id) {
        if (retroCompatibilityMode) {
            try {
                URI uri = new URI(id);
                id = uri.getPath().substring(1);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        return id;
    }

    protected List<SolrDocument> listDocuments(String collectionName, Map<String,String> queryParamMap) throws SearchEngineException, IOException {
        try {
            final QueryResponse response = client.query(collectionName, new MapSolrParams(queryParamMap));
            return response.getResults();
        } catch (BaseHttpSolrClient.RemoteSolrException | SolrServerException e) {
            log.error("Got solr exception from {}: {}", e.getClass().getSimpleName(), e.getMessage());
            if (StringUtils.contains(e.getMessage(), "undefined field")) {
                throw new BadQueryException(e.getMessage());
            }

            throw new SearchEngineException(e);
        }
    }

}
