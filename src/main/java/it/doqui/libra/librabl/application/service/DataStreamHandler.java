package it.doqui.libra.librabl.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.common.collect.ArrayListMultimap;
import it.doqui.libra.librabl.application.mappers.ContentConverter;
import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.application.mappers.PropertyConverter;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.application.model.properties.BufferDescriptor;
import it.doqui.libra.librabl.application.model.properties.ContentDestination;
import it.doqui.libra.librabl.application.model.properties.ExternalContentDescriptor;
import it.doqui.libra.librabl.application.model.properties.PropertyValueOperation;
import it.doqui.libra.librabl.application.model.session.UserContextMap;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.files.*;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.domain.service.MimeTypeService;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeLoaderDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.QueryContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.FileData;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static it.doqui.libra.librabl.domain.model.graph.Constants.CM_CONTENT;
import static it.doqui.libra.librabl.domain.model.session.UserContext.ROLE_POWERADMIN;
import static it.doqui.libra.librabl.domain.model.session.UserContext.ROLE_SYSADMIN;
import static it.doqui.libra.librabl.domain.policy.OperationOption.IGNORE_INVALID_CONTENT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@ApplicationScoped
@Slf4j
public class DataStreamHandler {

    @ConfigProperty(name = "libra.content-store.default-store", defaultValue = "store")
    String defaultContentStore;

    @ConfigProperty(name = "libra.content-store.tenant-in-path", defaultValue = "false")
    boolean includeTenantInContentPath;

    @ConfigProperty(name = "libra.content-store.reuse-document-files", defaultValue = "false")
    boolean reuseDocumentFiles;

    @ConfigProperty(name = "libra.content-store.tenant-isolation", defaultValue = "true")
    boolean globalTenantIsolationEnabled;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

    @Inject
    MimeTypeService mimeTypeService;

    @Inject
    UserContextMap userContextMap;

    @Inject
    AttachmentHelper contentRetriever;

    @Inject
    ContentRepository contentRepository;

    @Inject
    ContentConverter contentConverter;

    @Inject
    PropertyConverter propertyConverter;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    PermissionValidator permissionValidator;

    public Collection<? extends ContentProperty> downloadStreams(InputNodeRequest input) {
        var downloadedContents = new ArrayList<UploadedFileProperty>();
        var updatedProperties = new HashMap<String, Object>();
        for (var entry : input.getProperties().entrySet()) {
            if (entry.getValue() instanceof Collection<?> collection) {
                updatedProperties.put(entry.getKey(), collection.stream().map(item -> {
                    if (item == null) {
                        return null;
                    }

                    return Optional.ofNullable(preprocessProperty(item, downloadedContents)).orElse(item);
                }).toList());
            } else if (entry.getValue() != null) {
                var value = preprocessProperty(entry.getValue(), downloadedContents);
                if (value != null) {
                    updatedProperties.put(entry.getKey(), value);
                }
            }
        }

        if (!updatedProperties.isEmpty()) {
            input.getProperties().putAll(updatedProperties);
            log.debug("{} properties updated", updatedProperties.size());
            log.debug("{} content streams preprocessed", downloadedContents.size());
        }

        return downloadedContents;
    }

    private Object preprocessProperty(Object value, List<UploadedFileProperty> downloadedContents) {
        value = propertyConverter.convertValue(value);
        if (value instanceof ExternalContentDescriptor ecd) {
            value = preprocessProperty(retrieveSource(ecd, false), downloadedContents);
        } else if (value instanceof ContentStream cs && cs.getTarget() == ContentDestination.FILE) {
            value = createContentProperty(sessionContext.getTenant(), cs, cs.getInputStream());
        }

        if (value instanceof UploadedFileProperty cp) {
            downloadedContents.add(cp);
        }

        return value;
    }

    void processContentDescriptors(ActiveNode node, Set<String> contentPropertyNames, Set<OperationOption> optionSet) {
        var properties = node.getData().getProperties();
        if (contentPropertyNames != null && !contentPropertyNames.isEmpty()) {
            // contents organized by property name
            final Map<String, Map<String, ContentProperty>> cMap = buildContentsMap(node);
            final var incrementingContents = new HashMap<String, ContentProperty>();
            final var decrementingContents = new ArrayList<ContentProperty>();
            for (var pname : contentPropertyNames) {
                // previous contents of a property organized by url
                var pMap = cMap.get(pname);
                var hMap = new HashMap<String, ContentProperty>();
                if (pMap == null) {
                    pMap = new LinkedHashMap<>();
                } else {
                    for (var cp : pMap.values()) {
                        if (StringUtils.isNotBlank(cp.getHash())) {
                            hMap.put(cp.getHash(), cp);
                        }
                    }
                }

                List<ContentProperty> contents = new ArrayList<>();
                var pvalue = properties.remove(pname);
                if (pvalue != null) {
                    if (pvalue instanceof Collection<?> collection) {
                        var cps = new ArrayList<ContentProperty>();
                        for (var item : collection) {
                            cps.add(processContentItem(pname, item, node, contents));
                        }

                        if (cps.size() == 1) {
                            var cp = cps.get(0);
                            if (StringUtils.isBlank(cp.getMimetype()) && !pMap.isEmpty()) {
                                pMap.values().stream().map(ContentBasicDescriptor::getMimetype).filter(Objects::nonNull).findFirst().ifPresent(cp::setMimetype);
                            }
                        }
                    } else {
                        var cp = processContentItem(pname, pvalue, node, contents);
                        if (cp != null && StringUtils.isBlank(cp.getMimetype()) && !pMap.isEmpty()) {
                            pMap.values().stream().map(ContentBasicDescriptor::getMimetype).filter(Objects::nonNull).findFirst().ifPresent(cp::setMimetype);
                        }
                    }
                }

                var target = new ArrayList<ContentProperty>();
                for (ContentProperty cp : contents) {
                    if (StringUtils.isNotBlank(cp.getHash())) {
                        var h = hMap.get(cp.getHash());
                        if (h != null) {
                            log.debug("Replacing cp with previous version having url {}", h.getContentUrl());
                            transactionManagerPort.options().registerReplacedContentUrl(cp.getContentUrl());
                            h.setMimetype(Optional.ofNullable(cp.getMimetype()).orElse(h.getMimetype()));
                            h.setEncoding(Optional.ofNullable(cp.getEncoding()).orElse(h.getEncoding()));
                            h.setLocale(ObjectUtils.getAsString(Optional.ofNullable(cp.getLocale()).orElse(h.getLocale())));
                            h.setOpaque(cp.isOpaque());
                            cp = h;
                        }
                    }

                    var previous = pMap.remove(cp.getContentUrl());
                    if (previous == null) {
                        // the content url was not present in the content array;
                        // therefore, it must be added to the file table
                        incrementingContents.put(cp.getContentUrl(), cp);
                    } else {
                        // merge the new cp with the previous one
                        cp.mergeWith(previous);
                    }

                    if (cp.getOp() != null) {
                        switch (cp.getOp().getMode()) {
                            case ADD -> {
                                target.addAll(pMap.values());
                                pMap.clear();
                                target.add(cp);
                            }

                            case REPLACE, REMOVE -> {
                                if (!pMap.isEmpty()) {
                                    for (var prev : pMap.values()) {
                                        if (!Strings.CS.equals(prev.getFileName(), cp.getOp().getCurrentFileName())) {
                                            target.add(prev);
                                        } else if (!Strings.CS.equals(prev.getContentUrl(), cp.getContentUrl())) {
                                            decrementingContents.add(prev);
                                        }
                                    }

                                    pMap.clear();
                                } else if (!target.isEmpty()) {
                                    var newTarget = new ArrayList<ContentProperty>();
                                    for (var prev : target) {
                                        if (!Strings.CS.equals(prev.getFileName(), cp.getOp().getCurrentFileName())) {
                                            newTarget.add(prev);
                                        } else if (!Strings.CS.equals(prev.getContentUrl(), cp.getContentUrl())) {
                                            decrementingContents.add(prev);
                                        }
                                    }

                                    target = newTarget;
                                }

                                if (cp.getOp().getMode() != ContentOperationMode.REMOVE) {
                                    target.add(cp);
                                }
                            }
                        }
                    } else {
                        target.add(cp);
                    }
                }

                decrementingContents.addAll(pMap.values());

                // put all contents into the full map
                cMap.put(pname, target.stream().collect(Collectors.toMap(ContentProperty::getContentUrl, Function.identity())));
            } // end for each content property

            // find cp by hash and reuse the previous content url
            if (sessionContext.getTenantData().map(TenantData::getReuseDocumentFiles).orElse(reuseDocumentFiles)) {
                var createdFileSet = transactionManagerPort.options().getCreatedFileSet();
                var hashes = incrementingContents.values().stream()
                    .filter(cp -> cp.getContentUrl() != null && createdFileSet.contains(cp.getContentUrl()))
                    .map(ContentProperty::getHash)
                    .filter(StringUtils::isNotBlank)
                    .toList();

                var hMap = nodeDAO.mapContentsByHash(node.getTenant(), hashes);
                var urls = Set.copyOf(incrementingContents.keySet());
                for (var contentUrl : urls) {
                    var cp = incrementingContents.get(contentUrl);
                    var alternative = hMap.get(cp.getHash());
                    if (alternative != null && !Strings.CS.equals(alternative, cp.getContentUrl())) {
                        cp.setContentUrl(alternative);
                        incrementingContents.remove(contentUrl);
                        incrementingContents.put(alternative, cp);
                        log.debug("Replaced content url '{}' with '{}'", contentUrl, alternative);
                        transactionManagerPort.options().registerReplacedContentUrl(contentUrl);
                    }
                }
            }

            var mm = ArrayListMultimap.<String, ContentProperty>create();
            cMap.values().stream().flatMap(m -> m.values().stream()).forEach(cp -> mm.put(cp.getContentUrl(), cp));
            var noSizeCPs = new ArrayList<>(mm.values().stream().filter(Objects::nonNull).filter(cp -> cp.getSize() == null || cp.getSize() < 0).map(ContentProperty::getContentUrl).toList());
            if (!noSizeCPs.isEmpty()) {
                for (var cp0 : nodeDAO.findContentProperties(node.getTenant(), noSizeCPs)) {
                    var contentUrl = cp0.getFileURI().toString();
                    for (var cp : mm.get(contentUrl)) {
                        if (cp != null) {
                            cp.setSize(cp0.getSize());

                            if (cp.getHash() == null) {
                                cp.setHash(cp0.getHash());
                            }
                        }
                    }

                    noSizeCPs.remove(contentUrl);
                }

                node.getData().getAspects().remove("ecm-sys:invalidContent");
                if (!noSizeCPs.isEmpty()) {
                    for (var contentUrl : noSizeCPs) {
                        try {
                            var path = contentRepository.getPath(contentUrl);
                            var size = Files.size(path);
                            for (var cp : mm.get(contentUrl)) {
                                if (cp != null) {
                                    cp.setSize(size);
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Unable to calculate size for content url '{}': {}", contentUrl, e.getMessage());
                            if (optionSet == null || !optionSet.contains(IGNORE_INVALID_CONTENT)) {
                                throw new PreconditionFailedException("Unable to locate file '" + contentUrl + "' to calculate size");
                            } else {
                                node.getData().getAspects().add("ecm-sys:invalidContent");
                            }
                        }
                    }
                }
            }

            nodeDAO.incrementContentRef(node.getTenant(), incrementingContents.values());
            nodeDAO.decrementContentRef(node.getTenant(), decrementingContents);

            node.getData().getContents().clear();
            node.getData().getContents().addAll(
                    cMap.values().stream()
                            .flatMap(m -> m.values().stream())
                            .map(nodeMapper::toFileData)
                            .toList()
            );
        }
    }

    private Map<String, Map<String, ContentProperty>> buildContentsMap(ActiveNode node) {
        final Map<String, Map<String,ContentProperty>> cMap = new HashMap<>();
        for (FileData cp : node.getData().getContents()) {
            cMap.compute(cp.getName(), (k,v) -> {
                var map = v;
                if (v == null) {
                    map = new LinkedHashMap<>();
                }

                if (StringUtils.isNotBlank(cp.getContentUrl())) {
                    map.put(cp.getContentUrl(), nodeMapper.toContentProperty(cp, node));
                }

                return map;
            });
        }
        return cMap;
    }

    private ContentProperty processContentItem(String pname, Object item, ActiveNode node, final List<ContentProperty> contents) {
        if (item instanceof ContentBasicDescriptor cd) {
            var cp = createContentDescriptor(pname, node, cd);
            ObjectUtils.add(contents, cp);
            return cp;
        } else if (item instanceof PropertyValueOperation pvo) {
            var op = new ContentProperty.UpdateOperation();
            op.setMode(
                    switch (pvo.getOp()) {
                        case ADD -> ContentOperationMode.ADD;
                        case REMOVE -> ContentOperationMode.REMOVE;
                        case PUT -> ContentOperationMode.REPLACE;
                        default -> throw new IllegalArgumentException("Invalid property value operation");
                    }
            );
            op.setCurrentFileName(pvo.getKey());

            if (op.getMode() == ContentOperationMode.REMOVE) {
                var cp = new ContentProperty();
                cp.setName(pname);
                cp.setOp(op);
                contents.add(cp);
            } else if (pvo.getValue() instanceof ContentBasicDescriptor cd) {
                var cp = createContentDescriptor(pname, node, cd);
                if (cp != null) {
                    cp.setOp(op);
                    contents.add(cp);
                    return cp;
                }
            } else {
                throw new IllegalArgumentException("Invalid content operation value");
            }
        }

        return null;
    }

    private ContentProperty resolveTemplate(ContentProperty cp) {
        if (Strings.CS.contains(cp.getContentUrl(), "{")) {
            int slash = cp.getContentUrl().lastIndexOf('/');
            if (slash > 0) {
                var filename = StringUtils.stripToEmpty(cp.getContentUrl().substring(slash + 1));
                var cal = Calendar.getInstance();
                var store = sessionContext.getTenantData()
                    .map(TenantData::getDefaultStore)
                    .orElse(defaultContentStore);

                try {
                    var md5 = ObjectUtils.hash(filename, "MD5");
                    var hash0 = md5.substring(0, 2);
                    var hash1 = md5.substring(2, 4);
                    var hash2 = md5.substring(4, 6);

                    var contentUrl = cp.getContentUrl()
                        .replace("{store}", store)
                        .replace("{tenant}", sessionContext.getTenant())
                        .replace("{year}", String.valueOf(cal.get(Calendar.YEAR)))
                        .replace("{month}", String.valueOf(cal.get(Calendar.MONTH) + 1))
                        .replace("{day}", String.valueOf(cal.get(Calendar.DAY_OF_MONTH)))
                        .replace("{hour}", String.valueOf(cal.get(Calendar.HOUR_OF_DAY)))
                        .replace("{minute}", String.valueOf(cal.get(Calendar.MINUTE)))
                        .replace("{hash0}", hash0)
                        .replace("{hash1}", hash1)
                        .replace("{hash2}", hash2);

                    cp.setContentUrl(contentUrl);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return cp;
    }

    private ContentProperty createContentDescriptor(String pname, ActiveNode node, ContentBasicDescriptor cd) {
        if (cd != null) {
            if (cd.getName() == null) {
                cd.setName(pname);
            } else if (!Strings.CS.equals(cd.getName(), pname)) {
                throw new BadRequestException(String.format("Content descriptor property name does not match: found '%s' instead of '%s'", cd.getName(), pname));
            }
        }

        if (cd instanceof ContentProperty cp) {
            return resolveTemplate(cp);
        } else if (cd instanceof ContentStream cs) {
            if (cs.getInputStream() != null) {
                return createContentProperty(node.getTenant(), cs, cs.getInputStream());
            }
        } else if (cd instanceof ExternalContentDescriptor ecd) {
            var cc = retrieveSource(ecd, true);
            if (cc != null) {
                if (cc instanceof ContentProperty cp) {
                    return resolveTemplate(cp);
                } else if (cc instanceof NodeAttachment a && cc.getDescriptor() instanceof ContentProperty cp && a.getStore() != null) {
                    var store = a.getStore();
                    if (store.getTenant() != null) {
                        var tenantIsolationEnabled = tenantRepository.findByIdOptional(sessionContext.getTenant())
                                .map(TenantSpace::getData)
                                .map(TenantData::getTenantIsolationEnabled)
                                .orElse(globalTenantIsolationEnabled);

                        if (!Strings.CS.equals(store.getDbSchema(), sessionContext.getUserContext().getDbSchema())
                            || (tenantIsolationEnabled && !store.getTenant().equals(sessionContext.getTenant()))
                        ) {
                            nodeDAO.unCountContentRef(store.getDbSchema(), store.getTenant(), cp);
                            cp.setContentUrl(relocate(cp.getContentUrl(), a.getStore().getPath()));
                            nodeDAO.unCountContentRef(sessionContext.getUserContext().getDbSchema(), node.getTenant(), cp);
                        }
                    }

                    return cp;
                } else if (cc instanceof ContentStream cs) {
                    if (cs.getInputStream() != null) {
                        return createContentProperty(node.getTenant(), cs, cs.getInputStream());
                    }
                }
            }
        }

        return null;
    }

    private ContentContainer retrieveSource(ExternalContentDescriptor d, boolean inTx) {
        if (d.getSource() == null) {
            return null;
        }

        if (d.getSource().getUri() != null) {
            if (d.getSource().getRef() != null) {
                throw new BadRequestException("'uri' and 'ref' cannot be both specified in source of external content");
            }

            var uri = d.getSource().getUri();
            log.debug("Downloading source from URI '{}'", uri);
            if (Strings.CS.equals(uri.getScheme(), "http") || Strings.CS.equals(uri.getScheme(), "https")) {
                return createContentContainerFromHttpURL(uri, d);
            } else if (Strings.CS.equals(uri.getScheme(), "mem")) {
                var key = uri.getAuthority();
                var obj = userContextMap.getMap().get(key);
                if (obj instanceof NodeAttachment a) {
                    return createContentContainerFromAttachment(a, d, uri.getPath());
                } else if (obj instanceof File f) {
                    return createContentContainerFromFile(f, d, uri.getPath());
                } else {
                    throw new PreconditionFailedException("Unable to locate memory ref '" + key + "'");
                }
            } else if (Strings.CS.equals(uri.getScheme(), "uuid")) {
                if (!inTx) {
                    return null;
                }

                var auth = uri.getAuthority().split("@");
                var ref = new ContentRef().setUuid(auth[0]).setTenant(auth.length > 1 ? auth[1] : null);
                var a = getNodeContent(ref);
                return createContentContainerFromAttachment(a, d, uri.getPath());
            } else {
                if (sessionContext.getUserContext().isUserInRole(ROLE_SYSADMIN) || sessionContext.getUserContext().isUserInRole(ROLE_POWERADMIN)) {
                    try {
                        final File f;
                        if (Strings.CS.equals(uri.getScheme(), "file")) {
                            f = new File(uri.getPath());
                        } else {
                            var path = contentRepository.getPath(uri.toString());
                            log.debug("Looking for file '{}'", path);
                            f = path.toFile();
                        }

                        return createContentContainerFromFile(f, d, null);
                    } catch (IOException e) {
                        throw new SystemException(e);
                    }
                }

                log.warn("Got a forbidden source uri: {}", uri);
                throw new BadRequestException("Forbidden source uri " + uri);
            }
        } else if (d.getSource().getRef() != null) {
            if (!inTx) {
                return null;
            }

            var a = getNodeContent(d.getSource().getRef());
            if (StringUtils.isNotBlank(d.getName())) {
                a.getContentProperty().setName(d.getName());
            }

            if (StringUtils.isNotBlank(d.getMimetype())) {
                a.getContentProperty().setMimetype(d.getMimetype());
            }

            if (StringUtils.isNotBlank(d.getEncoding())) {
                a.getContentProperty().setEncoding(d.getEncoding());
            }

            if (StringUtils.isNotBlank(d.getFileName())) {
                a.getContentProperty().setFileName(d.getFileName());
            }

            if (d.getLocale() != null) {
                a.getContentProperty().setLocale(d.getLocale().toString());
            }

            return a;
        } else {
            throw new BadRequestException("Either 'uri' or 'ref' must be specified in source of external content");
        }
    }

    private ContentContainer createContentContainerFromHttpURL(URI uri, ExternalContentDescriptor d) {
        var request = HttpRequest.newBuilder()
            .uri(uri)
            .GET()
            .build();

        try {
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != HttpStatus.OK_200) {
                throw new SystemException(String.format("Unable to download content from URI '%s': got error code %d", uri, response.statusCode()));
            }

            var cs = new ContentStream();
            cs.setInputStream(response.body());
            cs.setName(Optional.ofNullable(d.getName()).orElse(CM_CONTENT));
            cs.setLocale(Optional.ofNullable(d.getLocale()).map(Locale::toString).orElse(null));
            cs.setMimetype(Optional.ofNullable(d.getMimetype()).orElse(response.headers().firstValue("Content-Type").orElse(null)));
            cs.setEncoding(d.getEncoding());
            cs.setFileName(Optional.ofNullable(d.getFileName()).orElse(IOUtils.getFileName(response.headers().firstValue("Content-Disposition").orElse(null))));
            cs.setTarget(d.getTarget());
            cs.setOpaque(d.isOpaque());

            cs = contentConverter.convert(cs, d.getConverter());
            if (Optional.ofNullable(d.getTarget()).orElse(ContentDestination.FILE) == ContentDestination.STREAM) {
                return BufferDescriptor.of(cs);
            }

            return createContentProperty(sessionContext.getTenant(), cs, cs.getInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SystemException(e);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    private ContentContainer createContentContainerFromAttachment(NodeAttachment a, ExternalContentDescriptor d, String innerPath) {
        if (StringUtils.isBlank(innerPath)) {
            return a;
        }

        return createContentContainerFromFile(a.getFile(), d, innerPath);
    }

    private ContentContainer createContentContainerFromInputStream(InputStream is, ExternalContentDescriptor d) {
        var cs = new ContentStream();
        cs.setInputStream(is);
        cs.setName(Optional.ofNullable(d.getName()).orElse(CM_CONTENT));
        cs.setLocale(Optional.ofNullable(d.getLocale()).map(Locale::toString).orElse(null));
        cs.setMimetype(d.getMimetype());
        cs.setEncoding(d.getEncoding());
        cs.setTarget(d.getTarget());
        cs.setOpaque(d.isOpaque());
        cs.setFileName(d.getFileName());

        if (StringUtils.isNotBlank(cs.getFileName()) && (StringUtils.isBlank(cs.getMimetype()) || Strings.CI.equals(cs.getMimetype(), APPLICATION_OCTET_STREAM))) {
            Optional.ofNullable(mimeTypeService.getMimeType(cs.getFileName())).ifPresent(cs::setMimetype);
        }

        cs = contentConverter.convert(cs, d.getConverter());
        if (Optional.ofNullable(d.getTarget()).orElse(ContentDestination.FILE) == ContentDestination.STREAM) {
            return BufferDescriptor.of(cs);
        }

        return createContentProperty(sessionContext.getTenant(), cs, cs.getInputStream());
    }

    private ContentContainer createContentContainerFromFile(File f, ExternalContentDescriptor d, String innerPath) {
        try {
            if (innerPath == null || StringUtils.isBlank(innerPath)) {
                return createContentContainerFromInputStream(Files.newInputStream(f.toPath()), d);
            }

            var path = URLDecoder.decode(innerPath.substring(1), StandardCharsets.UTF_8);
            try (ZipFile zipFile = new ZipFile(f)) {
                var zipEntry = zipFile.getEntry(path);
                if (zipEntry != null) {
                    var result = createContentContainerFromInputStream(zipFile.getInputStream(zipEntry), d);
                    if (result instanceof ContentStream cs) {
                        return BufferDescriptor.of(cs);
                    }
                    return result;
                }

                throw new PreconditionFailedException(String.format("Unable to find zip entry '%s'", path));
            }
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    private UploadedFileProperty createContentProperty(String tenant, ContentDescriptor descriptor, final InputStream stream) {
        try {
            UploadedFileProperty cp = new UploadedFileProperty();
            var md = MessageDigest.getInstance("SHA-256");
            try (var is = new DigestInputStream(stream, md)) {
                cp.setName(descriptor.getName());
                cp.setFileName(descriptor.getFileName());
                cp.setEncoding(descriptor.getEncoding());
                cp.setOpaque(descriptor.isOpaque());
                if (descriptor instanceof ContentMetadataProvider cmp) {
                    cmp.addMetadata(cp);
                }

                if (StringUtils.isNotBlank(descriptor.getMimetype())) {
                    try {
                        var mimetypes = Arrays.stream(descriptor.getMimetype().split(", ")).toList();
                        for (String mimetype : mimetypes) {
                            var mt = MediaType.valueOf(mimetype);
                            //TODO: riportare la lista dei mimetype da escludere dalla fulltext su db
                            var simpleMimeType = (mt.getType() + "/" + mt.getSubtype()).toLowerCase();
                            if (Strings.CS.containsAny(simpleMimeType, "zip", "rar", "tar", "jar", "7z", "compressed")) {
                                cp.setOpaque(true);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Invalid mime type " + descriptor.getMimetype() + ": " + e.getMessage());
                    }

                    cp.setMimetype(descriptor.getMimetype());
                }

                // format example contentUrl=store://2012/3/23/16/46/60b73f1b-74ff-11e1-aeda-b7ce474e1849.bin
                var cal = Calendar.getInstance();
                var store = sessionContext.getTenantData()
                    .map(TenantData::getDefaultStore)
                    .orElse(defaultContentStore);

                var contentUrl = String.format("%s://%d/%s%d/%d/%d/%d/%s.bin",
                    store,
                    cal.get(Calendar.YEAR),
                    includeTenantInContentPath ? tenant + "/" : "",
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    UuidCreator.getTimeOrderedEpoch()
                );
                cp.setContentUrl(contentUrl);

                long size = contentRepository.writeStream(contentUrl, is);
                log.debug("size = {}", size);
                cp.setSize(size);
            }

            var digest= HexFormat.of().formatHex(md.digest());
            log.debug("digest = {}", digest);
            cp.setHash(digest);
            transactionManagerPort.options().registerCreatedContentUrl(cp.getContentUrl());
            return cp;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new SystemException(e);
        }
    }

    private String relocate(String contentUrl, Path path) {
        try {
            var candidatePath = contentRepository.getStorePath(contentUrl);
            if (candidatePath != null && candidatePath.equals(path)) {
                return contentUrl;
            }

            var stores = contentRepository.getStoresOfPath(path);
            if (stores.isEmpty()) {
                throw new BadDataException(String.format("Unable to relocate content '%s' into current tenant", contentUrl));
            }

            URI uri = new URI(contentUrl);
            var scheme = uri.getScheme();
            if (!stores.contains(scheme)) {
                return contentUrl.replace(scheme, stores.stream().findFirst().orElse(scheme));
            }

            return contentUrl;
        } catch (URISyntaxException e) {
            throw new SystemException(e);
        }
    }

    public NodeAttachment getNodeContent(ContentRef contentRef) {
        Supplier<NodeAttachment> f = () -> {
            try {
                log.debug("Searching for node {}, cppn: {}, fileName: {}, tenant: {}, schema: {}", contentRef.getUuid(), contentRef.getContentPropertyName(), contentRef.getFileName(), sessionContext.getTenant(), sessionContext.getUserContext().getDbSchema());
                var n = nodeLoaderDAO
                        .getNode(new Vertex(VertexType.UUID, contentRef.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                        .map(node -> permissionValidator.requirePermission(node, PermissionFlag.R))
                        .orElseThrow(() -> new NotFoundException(contentRef.getUuid()));
                var fd = Optional.ofNullable(n.getData().getFileData(contentRef.getContentPropertyName(), contentRef.getFileName()))
                        .orElseThrow(PreconditionFailedException::new);
                return contentRetriever.attachment(n.getData(), fd);
            } catch (IOException e) {
                throw new SystemException(e);
            } catch (PreconditionFailedException e) {
                throw new BadRequestException(e.getMessage());
            }
        };

        final NodeAttachment a;
        final var tenant = sessionContext.getTenant();
        if (StringUtils.isBlank(contentRef.getTenant()) || Strings.CI.equals(tenant, contentRef.getTenant())) {
            log.debug("Searching in tenant {} (current tenant: {})", contentRef.getTenant(), tenant);
            a = f.get();
        } else {
            var authorityRef = new AuthorityRef(contentRef.getIdentity() == null ? "admin" : contentRef.getIdentity(), TenantRef.valueOf(contentRef.getTenant()));
            a = transactionManagerPort.doAsUser(authorityRef, f);
        }

        return a;
    }
}
