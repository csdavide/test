package it.doqui.libra.librabl.business.provider.integration.solr;

import it.doqui.libra.librabl.business.provider.schema.impl.TenantSchema;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.views.schema.IndexableProperty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.ConfigSetAdminResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.util.NamedList;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
@Slf4j
@SuppressWarnings("unchecked")
public class SolrManager extends AbstractSolrController {

    @ConfigProperty(name = "solr.configSetPath", defaultValue = "./res/solr-configset")
    String configSetPath;

    @ConfigProperty(name = "solr.numShards", defaultValue = "1")
    int numShards;

    @ConfigProperty(name = "solr.numReplicas", defaultValue = "3")
    int numReplicas;

    @ConfigProperty(name = "solr.defaultTokenizationType", defaultValue = "it")
    String defaultTokenizationType;

    public void deleteTransactions(TenantRef tenantRef, Collection<String> tx) {
        var collectionName = collectionName(tenantRef);
        deleteTransactions(collectionName, tx);
        deleteTransactions(collectionName + "-sg", tx);
    }

    private void deleteTransactions(String collectionName, Collection<String> tx) {
        try {
            var q = tx.stream().collect(Collectors.joining(" ", "TX:(", ")"));
            UpdateResponse response = client.deleteByQuery(collectionName, q);
            if (response.getStatus() != 0) {
                String msg = String.format(
                    "Solr returned status %d removing documents", response.getStatus()
                );
                throw new RuntimeException(msg);
            }
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanTenant(TenantRef tenantRef) {
        var collectionName = collectionName(tenantRef);
        try {
            var collections = findAllCollections();
            var configSets = findAllConfigSets();
            deleteCollection(collections, configSets, collectionName);
            deleteCollection(collections, configSets, collectionName + "-sg");
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteCollection(final Set<String> collections, final Set<String> configSets, final String collectionName) throws SolrServerException, IOException {
        if (collections.contains(collectionName)) {
            log.debug("Removing collection {} from {}", collectionName, collections);
            var request = CollectionAdminRequest.deleteCollection(collectionName);
            final NamedList<Object> response = client.request(request);
            log.debug("Solr deleteCollection returned {}", response);
        }

        if (configSets.contains(collectionName)) {
            removeConfigSet(collectionName);
        }
    }

    public void createTenant(TenantRef tenantRef, boolean overwrite) {
        if (retroCompatibilityMode || fakeIndexModeEnabled) {
            return;
        }

        try {
            String collectionName = collectionName(tenantRef);
            String sgCollectionName = collectionName + "-sg";

            final Set<String> configSet;
            final Set<String> collectionSet;
            try {
                configSet = findAllConfigSets();
                collectionSet = findAllCollections();
            } catch (SolrServerException | IOException e) {
                log.warn("Unable to find collections and configs: {}", e.getMessage());
                throw e;
            }

            createCollection(collectionName, overwrite, configSet, collectionSet);
            createCollection(sgCollectionName, overwrite, configSet, collectionSet);
        } catch (SolrServerException | IOException e) {
            // ignore
        }
    }

    private void createCollection(String collectionName, boolean overwrite, Set<String> configSet, Set<String> collectionSet) throws IOException, SolrServerException {
        try {
            if (!configSet.contains(collectionName)) {
                uploadConfigSet(collectionName, overwrite);
                log.info("ConfigSet {} created", collectionName);
            } else {
                log.warn("ConfigSet {} already exists", collectionName);
            }

            if (!collectionSet.contains(collectionName)) {
                createCollection(collectionName);
                log.info("Collection {} created", collectionName);
            } else {
                log.warn("Collection {} already exists", collectionName);
            }
        } catch (SolrServerException | IOException e) {
            log.warn("Unable to create collection {}: {}", collectionName, e.getMessage());
            throw e;
        }
    }

    private void uploadConfigSet(String collectionName, boolean overwrite) throws IOException, SolrServerException {
        final File configSetPathFile;
        if (configSetPath.endsWith(".zip")) {
            configSetPathFile = new File(configSetPath);
        } else {
            configSetPathFile = new File(configSetPath + ".zip");
            log.info("Creating configSet zip file '{}'", configSetPathFile);
            try (FileOutputStream fos = new FileOutputStream(configSetPathFile)) {
                try (ZipOutputStream zipOut = new ZipOutputStream(fos)) {
                    File fileToZip = new File(configSetPath);
                    zipFile(fileToZip, null, zipOut);
                }
            }
        }

        final SolrRequest<ConfigSetAdminResponse> request = new ConfigSetAdminRequest.Upload()
            .setConfigSetName(collectionName)
            .setOverwrite(overwrite)
            .setUploadFile(configSetPathFile, "application/octet-stream");
        final NamedList<Object> response = client.request(request);
        log.debug("Solr uploadConfigSet returned {}", response);
    }

    private void removeConfigSet(String collectionName) throws SolrServerException, IOException {
        final SolrRequest<ConfigSetAdminResponse> request = new ConfigSetAdminRequest.Delete()
            .setConfigSetName(collectionName);
        final NamedList<Object> response = client.request(request);
        log.debug("Solr removeConfigSet returned {}", response);
    }

    @SuppressWarnings("unchecked")
    private Set<String> findAllConfigSets() throws SolrServerException, IOException {
        final SolrRequest<ConfigSetAdminResponse.List> request = new ConfigSetAdminRequest.List();
        final NamedList<Object> response = client.request(request);
        log.debug("Solr findAllConfigSets returned {}", response);
        final List<String> configSets = (List<String>) response.get("configSets");
        return new HashSet<>(configSets);
    }

    public Set<String> findAllCollections() throws SolrServerException, IOException {
        final List<String> collections = CollectionAdminRequest.listCollections(client);
        return new HashSet<>(collections);
    }

    private void createCollection(String collectionName) throws SolrServerException, IOException {
        log.info("Creating collection {} with {} shards and {} replicas", collectionName, numShards, numReplicas);
        final SolrRequest<CollectionAdminResponse> request = CollectionAdminRequest.createCollection(collectionName, collectionName, numShards, numReplicas);
        final NamedList<Object> response = client.request(request);
        log.debug("Solr createCollection returned {}", response);
    }

    private void syncCoreFields(String collectionName, Map<String, SolrField> fieldMap) {
        if (!configSetPath.endsWith(".zip")) {
            log.info("Synchronize core fields to collection {}", collectionName);
            try {
                var configFile = new File(configSetPath, "managed-schema");
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                final DocumentBuilder builder = dbf.newDocumentBuilder();
                final Document doc = builder.parse(configFile);
                var fieldContainerList = doc.getElementsByTagName("fields");
                if (fieldContainerList.getLength() > 0) {
                    var fields = ((Element)fieldContainerList.item(0)).getElementsByTagName("field");
                    for (int i = 0; i < fields.getLength(); i++) {
                        var fieldElem = (Element)fields.item(i);
                        var name = fieldElem.getAttribute("name");
                        if (StringUtils.isAllUpperCase(name)) {
                            var solrField = fieldMap.get(name);
                            var f = new SolrField();
                            f.setName(name);
                            f.setType(fieldElem.getAttribute("type"));
                            f.setMultiValued(BooleanUtils.toBoolean(fieldElem.getAttribute("multiValued")));
                            f.setIndexed(BooleanUtils.toBoolean(fieldElem.getAttribute("indexed")));
                            f.setStored(BooleanUtils.toBoolean(fieldElem.getAttribute("stored")));

                            if (solrField == null) {
                                log.debug("Adding field name '{}'", name);
                                addField(collectionName, f);
                            } else if (!StringUtils.equals(f.getType(), solrField.getType())
                                || f.isIndexed() != solrField.isIndexed()
                                || f.isStored() != solrField.isStored()
                                || f.isMultiValued() != solrField.isMultiValued()) {
                                log.debug("Updating field name '{}'", name);
                                updateField(collectionName, f);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new SystemException("Unable to process solr configuration: " + e.getMessage(), e);
            }

        }
    }

    private void syncCoreDynamicFields(String collectionName, Map<String, DynamicSolrField> fieldMap) {
        if (!configSetPath.endsWith(".zip")) {
            log.info("Synchronize core dynamic fields to collection {}", collectionName);
            try {
                var configFile = new File(configSetPath, "managed-schema");
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                final DocumentBuilder builder = dbf.newDocumentBuilder();
                final Document doc = builder.parse(configFile);
                var fieldContainerList = doc.getElementsByTagName("fields");
                if (fieldContainerList.getLength() > 0) {
                    var fields = ((Element)fieldContainerList.item(0)).getElementsByTagName("dynamicField");
                    for (int i = 0; i < fields.getLength(); i++) {
                        var fieldElem = (Element)fields.item(i);
                        var name = fieldElem.getAttribute("name");
                        var solrField = fieldMap.get(name);
                        var f = new SolrField();
                        f.setName(name);
                        f.setType(fieldElem.getAttribute("type"));
                        f.setMultiValued(BooleanUtils.toBoolean(fieldElem.getAttribute("multiValued")));
                        f.setIndexed(BooleanUtils.toBoolean(fieldElem.getAttribute("indexed")));
                        f.setStored(BooleanUtils.toBoolean(fieldElem.getAttribute("stored")));

                        if (solrField == null) {
                            log.debug("Adding dynamic field name '{}'", name);
                            addDynamicField(collectionName, f);
                            fieldMap.put(f.getName(), asDynamicSolrField(f));
                        } else if (!StringUtils.equals(f.getType(), solrField.getType())
                            || f.isIndexed() != solrField.isIndexed()
                            || f.isStored() != solrField.isStored()
                            || f.isMultiValued() != solrField.isMultiValued()) {
                            log.debug("Updating dynamic field name '{}'", name);
                            updateDynamicField(collectionName, f);
                            fieldMap.put(f.getName(), asDynamicSolrField(f));
                        }
                    }
                }
            } catch (Exception e) {
                throw new SystemException("Unable to process solr configuration: " + e.getMessage(), e);
            }
        }
    }

    public void syncTenant(TenantRef tenantRef, TenantSchema schema) {
        syncTenant(tenantRef, schema, false);
    }

    public void syncTenant(TenantRef tenantRef, TenantSchema schema, boolean coreFields) {
        log.info("Synchronize tenant {} schema {}", tenantRef, schema.getTenant());
        String collectionName = collectionName(tenantRef);
        try {
            Map<String, SolrField> fieldMap = findFields(collectionName);
            Map<String, DynamicSolrField> dynamicFieldMap = findDynamicFields(collectionName);
            if (coreFields) {
                syncCoreFields(collectionName, fieldMap);
                syncCoreDynamicFields(collectionName, dynamicFieldMap);
            }

            var dynamicFields = new ArrayList<>(dynamicFieldMap.values());
            var modelSet = new HashSet<String>();
            for (CustomModelSchema model : schema.getNamespaces().values()) {
                if (modelSet.contains(model.getModelName())) {
                    continue;
                }

                log.debug("Analyzing model {} ({} properties)", model.getModelName(), model.getProperties().size());
                modelSet.add(model.getModelName());
                for (var pd : model.getProperties()) {
                    var fieldName = "@" + pd.getName();
                    log.trace("Processing property field {} (multiple: {}, indexed: {}, tokenized: {}, reverse: {})", fieldName, pd.isMultiple(), pd.isIndexed(), pd.isTokenized(), pd.isReverseTokenized());
                    if (!pd.isIndexed() && !pd.isStored()) {
                        continue;
                    }

                    SolrField f = fieldMap.get(fieldName);
                    String type = mapAsSolrType(model, pd);
                    log.debug("Field {} with type {} mapped as {}", fieldName, pd.getType(), type);
                    if (f == null) {
                        var d = matches(dynamicFieldMap.values(), fieldName);
                        if (d != null) {
                            if (StringUtils.equals(d.getType(), type)
                                && (d.isMultiValued() == pd.isMultiple())
                                && (d.isIndexed() || !pd.isIndexed())
                                && (d.isStored() || !pd.isStored())) {
                                log.info("Property {} matches dynamic field {}", fieldName, d.getName());
                                continue;
                            } else {
                                log.trace("Property {} does not fully matches dynamic field {}: either type ({}) or attributes do not match", fieldName, d.getName(), type);
                            }
                        } else {
                            log.warn("Property {} missing in solr schema", fieldName);
                        }

                        if (!retroCompatibilityMode && !fakeIndexModeEnabled && type != null) {
                            addField(collectionName, mapAsSolrField(pd, type));
                        }
                    } else {
                        if (type != null) {
                            boolean updateRequired = false;
                            if (f.isMultiValued() != pd.isMultiple()) {
                                log.warn("Property {}: attribute multiValued does not match", fieldName);
                                updateRequired = true;
                            }

                            if (f.isIndexed() != pd.isIndexed()) {
                                log.warn("Property {}: attribute indexed does not match", fieldName);
                                updateRequired = true;
                            }

                            if (f.isStored() != pd.isStored()) {
                                log.warn("Property {}: attribute stored does not match", fieldName);
                                updateRequired = true;
                            }

                            if (!StringUtils.equals(type, f.getType())) {
                                log.debug("Property {} schema type {} solr type {} tokenized {} (reverse {})", fieldName, type, f.getType(), pd.isTokenized(), pd.isReverseTokenized());
                                updateRequired = true;
                            }

                            if (updateRequired && !retroCompatibilityMode && !fakeIndexModeEnabled) {
                                updateField(collectionName, mapAsSolrField(pd, type));
                            }
                        }
                    } // end if type not null
                } // end for pd

                for (var dp : model.getDynamicProperties()) {
                    if (!dp.isIndexed()) {
                        continue;
                    }

                    var fieldName = "@" + dp.getName();
                    log.trace("Processing dynamic property field {}", fieldName);

                    var type = mapAsSolrType(model, dp);
                    var d = matches(dynamicFields, fieldName);
                    if (d == null) {
                        if (dp.isPredefined()) {
                            throw new PreconditionFailedException("No dynamic field found matching the predefined dynamic property " + fieldName);
                        } else {
                            log.info("No dynamic field found matching {}. Adding a new one", fieldName);
                            if (!retroCompatibilityMode && !fakeIndexModeEnabled) {
                                var f = mapAsSolrField(dp, type);
                                addDynamicField(collectionName, f);
                                dynamicFields.add(0, asDynamicSolrField(f));
                            }
                        }
                    } else {
                        if (StringUtils.equals(d.getType(), type)
                            && (d.isMultiValued() || !dp.isMultiple())
                            && (d.isIndexed() || !dp.isIndexed())
                            && (d.isStored() || !dp.isStored())) {
                            log.info("Dynamic property {} matches dynamic field {}", fieldName, d.getName());
                        } else {
                            log.warn("Dynamic property {} does not fully matches dynamic field {}: either type or attributes do not match", fieldName, d.getName());

                            if (StringUtils.equals(d.getName(), fieldName)) {
                                log.warn("Updating the dynamic field {}", d.getName());
                                d.setType(type);
                                d.setMultiValued(dp.isMultiple());
                                d.setIndexed(dp.isIndexed());
                                d.setStored(dp.isStored());
                                updateDynamicField(collectionName, d);
                            } else if (noStar(fieldName).startsWith(noStar(d.getName()))) {
                                log.warn("A more specific dynamic field required: {}", dp.getName());
                                var f = mapAsSolrField(dp, type);
                                addDynamicField(collectionName, f);
                                dynamicFields.add(0, asDynamicSolrField(f));
                            } else {
                                log.warn("Dynamic property {} too generic", dp.getName());
                            }
                        }
                    }
                } // end for dp
            } // end for model
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String noStar(String s) {
        return s.replace("*", "");
    }

    private DynamicSolrField matches(Collection<DynamicSolrField> dynamicFields, String name) {
        for (DynamicSolrField f : dynamicFields) {
            if (f.getPattern().matcher(name).matches()) {
                return f;
            }
        }
        return null;
    }

    private SolrField mapAsSolrField(IndexableProperty pd, String type) {
        SolrField f = new SolrField();
        f.setName("@" + pd.getName());
        f.setType(type);
        f.setMultiValued(pd.isMultiple());
        f.setIndexed(pd.isIndexed());
        f.setStored(pd.isStored());

        return f;
    }

    private String mapAsSolrType(CustomModelSchema model, IndexableProperty pd) {
        if (pd.getType() == null) {
            return null;
        }

        PrefixedQName qtype = PrefixedQName.valueOf(pd.getType());
        if (StringUtils.equals(qtype.getNamespaceURI(), "d")) {
            switch (qtype.getLocalPart()) {
                case "content":
                    return null;

                case "string":
                    return "text";

                case "mltext":
                case "text":
                    final String suffix;
                    if (pd.isTokenized()) {
                        suffix = Optional.ofNullable(pd.getTokenizationType())
                            .orElse(
                                Optional.ofNullable(model.getDefaultTokenizationType()).orElse(defaultTokenizationType)
                            );
                    } else {
                        suffix = "ci";
                    }

                    return "text_" + suffix;

                default:
                    return qtype.getLocalPart();
            }
        }

        return pd.getType();
    }

    private void addDynamicField(String collectionName, SolrField f) throws SolrServerException, IOException {
        try {
            final var request = new SchemaRequest.AddDynamicField(asFieldAttrs(f));
            final var response = request.process(client, collectionName);
            log.debug("Solr addDynamicField '{}' returned {}", f.getName(), response);
        } catch (BaseHttpSolrClient.RemoteExecutionException e) {
            if (e.code() == 400) {
                throw new BadRequestException(e.getMessage());
            }

            throw e;
        }
    }

    private void updateDynamicField(String collectionName, SolrField f) throws SolrServerException, IOException {
        try {
            final var request = new SchemaRequest.ReplaceDynamicField(asFieldAttrs(f));
            final var response = request.process(client, collectionName);
            log.debug("Solr updateDynamicField '{}' returned {}", f.getName(), response);
        } catch (BaseHttpSolrClient.RemoteExecutionException e) {
            if (e.code() == 400) {
                throw new BadRequestException(e.getMessage());
            }

            throw e;
        }
    }

    private void addField(String collectionName, SolrField f) throws SolrServerException, IOException {
        try {
            final SolrRequest<SchemaResponse.UpdateResponse> request = new SchemaRequest.AddField(asFieldAttrs(f));
            final SchemaResponse.UpdateResponse response =  request.process(client, collectionName);
            log.debug("Solr addField '{}' returned {}", f.getName(), response);
            if (response.getStatus() != 0) {
                String msg = String.format("Solr returned status %d adding field to schema", response.getStatus());
                throw new WebException(response.getStatus(), msg);
            }
        } catch (BaseHttpSolrClient.RemoteExecutionException e) {
            if (e.code() == 400) {
                throw new BadRequestException(e.getMessage());
            }

            throw e;
        }
    }

    private void updateField(String collectionName, SolrField f) throws SolrServerException, IOException {
        try {
            final SolrRequest<SchemaResponse.UpdateResponse> request = new SchemaRequest.ReplaceField(asFieldAttrs(f));
            final SchemaResponse.UpdateResponse response =  request.process(client, collectionName);
            log.debug("Solr updateField '{}' returned {}", f.getName(), response);
            if (response.getStatus() != 0) {
                String msg = String.format("Solr returned status %d updating field to schema", response.getStatus());
                throw new WebException(response.getStatus(), msg);
            }
        } catch (BaseHttpSolrClient.RemoteExecutionException e) {
            if (e.code() == 400) {
                throw new BadRequestException(e.getMessage());
            }

            throw e;
        }
    }

    private Map<String, Object> asFieldAttrs(SolrField f) {
        Map<String, Object> fieldAttributes = new HashMap<>();
        fieldAttributes.put("name", f.getName());
        fieldAttributes.put("type", f.getType());
        fieldAttributes.put("stored", f.isStored());
        fieldAttributes.put("indexed", f.isIndexed());
        fieldAttributes.put("multiValued", f.isMultiValued());
        fieldAttributes.put("required", false);

        return fieldAttributes;
    }

    private Map<String, SolrField> findFields(String collectionName) throws SolrServerException, IOException {
        final SolrRequest<SchemaResponse.FieldsResponse> request = new SchemaRequest.Fields();
        final SolrResponse response = request.process(client, collectionName);
        final NamedList<Object> list = response.getResponse();
        final List<NamedList<Object>> fieldList = (List<NamedList<Object>>) list.get("fields");
        final Map<String, SolrField> fieldMap = new HashMap<>();
        if (fieldList != null) {
            for (NamedList<Object> item : fieldList) {
                SolrField f = mapField(item);
                fieldMap.put(f.getName(), f);
            }
        }

        return fieldMap;
    }

    private Map<String, DynamicSolrField> findDynamicFields(String collectionName) throws SolrServerException, IOException {
        final SolrRequest<SchemaResponse.DynamicFieldsResponse> request = new SchemaRequest.DynamicFields();
        final SolrResponse response = request.process(client, collectionName);
        final NamedList<Object> list = response.getResponse();
        final List<NamedList<Object>> fieldList = (List<NamedList<Object>>) list.get("dynamicFields");
        final Map<String, DynamicSolrField> fieldMap = new LinkedHashMap<>();
        if (fieldList != null) {
            for (NamedList<Object> item : fieldList) {
                var f = mapField(item);
                log.debug("Retrieved dynamic field {}", f.getName());
                fieldMap.put(f.getName(), asDynamicSolrField(f));
            }
        }

        return fieldMap;
    }

    private DynamicSolrField asDynamicSolrField(SolrField f) {
        String regex = f.getName();
        regex = regex.replace("\\","\\\\");
        regex = regex.replace(".", "\\.");
        regex = regex.replace("*","\\*");
        regex = regex.replace("?","\\?");
        regex = regex.replace(".","\\.");
        regex = regex.replace("\\*",".*");
        regex = "^" + regex + "$";
        return new DynamicSolrField(f).compile(regex);
    }

    private SolrField mapField(NamedList<Object> item) {
        SolrField f = new SolrField();
        f.setName(ObjectUtils.getAsString(item.get("name")));
        f.setType(ObjectUtils.getAsString(item.get("type")));
        f.setMultiValued(ObjectUtils.getAsBoolean(item.get("multiValued"), false));
        f.setIndexed(ObjectUtils.getAsBoolean(item.get("indexed"), false));
        f.setStored(ObjectUtils.getAsBoolean(item.get("stored"), false));
        return f;
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }

        if (fileToZip.isDirectory()) {
            if (fileName != null) {
                if (fileName.endsWith("/")) {
                    zipOut.putNextEntry(new ZipEntry(fileName));
                    zipOut.closeEntry();
                } else {
                    zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                    zipOut.closeEntry();
                }
            }

            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    String name = fileName == null ? childFile.getName() : fileName + "/" + childFile.getName();
                    zipFile(childFile, name, zipOut);
                }
            }

            return;
        }

        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zipOut.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        }
    }

    @Getter
    private static class DynamicSolrField extends SolrField {
        private Pattern pattern;

        public DynamicSolrField(SolrField f) {
            this.setName(f.getName());
            this.setType(f.getType());
            this.setMultiValued(f.isMultiValued());
            this.setIndexed(f.isIndexed());
            this.setStored(f.isStored());
            this.pattern = null;
        }

        public DynamicSolrField compile(String regex) {
            this.pattern = Pattern.compile(regex);
            return this;
        }
    }
}
