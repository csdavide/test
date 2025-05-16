package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.*;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.Association;
import it.doqui.libra.librabl.business.provider.data.entities.NodePath;
import it.doqui.libra.librabl.business.provider.data.entities.SecurityGroup;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.provider.mappers.NodeMapper;
import it.doqui.libra.librabl.business.provider.mappers.PropertyConverter;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.*;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.business.service.node.PropertyContainer;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.PropertyValueOperation;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.LinkItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.node.*;
import it.doqui.libra.librabl.views.schema.AspectDescriptor;
import it.doqui.libra.librabl.views.schema.PropertyDescriptor;
import it.doqui.libra.librabl.views.schema.TypedInterfaceDescriptor;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.util.CollectionUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.mappers.PropertyConverter.TYPE_CONTENT;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;
import static it.doqui.libra.librabl.views.node.InputNodeRequest.AspectOperation.ADD;
import static it.doqui.libra.librabl.views.node.MapOption.ACL;
import static it.doqui.libra.librabl.views.node.MapOption.CHECK_ARCHIVE;
import static it.doqui.libra.librabl.views.node.NodeOperation.NodeOperationType.DELETE;
import static it.doqui.libra.librabl.views.node.OperationOption.*;

@ApplicationScoped
@Slf4j
public class NodeManager implements NodeService, NodeContentService {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS");

    @ConfigProperty(name = "libra.content-store.default-store", defaultValue = "store")
    String defaultContentStore;

    @ConfigProperty(name = "libra.content-store.tenant-in-path", defaultValue = "false")
    boolean includeTenantInContentPath;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    ModelManager modelManager;

    @Inject
    PropertyConverter propertyConverter;

    @Inject
    NodeValidator nodeValidator;

    @Inject
    ContentStoreService contentStoreManager;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    ArchiveDAO archiveDAO;

    @Inject
    AclDAO aclDAO;

    @Inject
    AssociationDAO associationDAO;

    @Inject
    LinkManager linkManager;

    @Inject
    SimpleNodeAccessManager simpleNodeAccessManager;

    @Inject
    MultipleNodeOperationService multipleNodeOperationService;

    @Inject
    VersionDAO versionDAO;

    @Inject
    ContentRetriever contentRetriever;

    final Set<String> generatedPropertySet = Set.of("sys:node-dbid", "sys:node-uuid", "sys:store-protocol", "sys:store-identifier", "ecm-sys:version", "ecm-sys:dataModifica");
    final Set<String> managedAspects = Set.of(ASPECT_CM_AUDITABLE,ASPECT_CM_WORKINGCOPY,ASPECT_COPIED_NODE);

    @Override
    public Optional<NodeItem> getNodeMetadata(long id, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        var n = nodeDAO
            .findNodeById(id, optionSet, QueryScope.DEFAULT)
            .filter(node -> StringUtils.equalsIgnoreCase(node.getTenant(), UserContextManager.getContext().getTenantRef().toString()));

        if (n.isPresent()) {
            if (StringUtils.equalsIgnoreCase(n.get().getTenant(), UserContextManager.getContext().getTenantRef().toString())) {
                throw new ForbiddenException("Node found in a different tenant");
            }
        } else {
            if (optionSet.contains(CHECK_ARCHIVE)) {
                var an = archiveDAO.getNode(id);
                if (an.isPresent()) {
                    var archivedNode = an.get();
                    var e = new NotFoundException("DBID: " + id);
                    e.getDetailMap().put("uuid", archivedNode.getUuid());
                    e.getDetailMap().put("nodeid", "" + archivedNode.getId());
                    e.getDetailMap().put("archived", "true");
                    throw e;
                }
            }

            return Optional.empty();
        }

        return n.map(node -> map(node, optionSet, filterPropertyNames, locale));
    }

    @Override
    public Optional<NodeItem> getNodeMetadata(String uuid, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        var n = nodeDAO
            .findNodeByUUID(uuid, optionSet, QueryScope.DEFAULT);

        if (n.isEmpty()) {
            if (optionSet.contains(CHECK_ARCHIVE)) {
                var an = archiveDAO.getNode(uuid);
                if (an.isPresent()) {
                    var archivedNode = an.get();
                    var e = new NotFoundException(uuid);
                    e.getDetailMap().put("uuid", archivedNode.getUuid());
                    e.getDetailMap().put("nodeid", "" + archivedNode.getId());
                    e.getDetailMap().put("archived", "true");
                    throw e;
                }
            }

            return Optional.empty();
        }

        return n.map(node -> map(node, optionSet, filterPropertyNames, locale));
    }

    private NodeItem map(ActiveNode node, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        int rights = permissionValidator.permissions(node);
        permissionValidator.requirePermission(node, rights, PermissionFlag.R);
        var result = nodeMapper.asNodeItem(node, optionSet, filterPropertyNames, locale);
        result.setRights(PermissionFlag.formatAsHumanReadable(rights));
        return result;
    }

    private NodeItem map(ActiveNode node, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, boolean checkAcl) {
        final String readableRights;
        if (checkAcl) {
            int rights = permissionValidator.permissions(node);
            if (!PermissionFlag.R.match(rights)) {
                return null;
            }

            readableRights = PermissionFlag.formatAsHumanReadable(rights);
        } else {
            readableRights = null;
        }

        var result = nodeMapper.asNodeItem(node, optionSet, filterPropertyNames, locale);
        result.setRights(readableRights);
        return result;
    }

    @Override
    public Optional<NodeInfoItem> getNodeInfo(String uuid) {
        return nodeDAO.findNodeIdWhereUUID(uuid);
    }

    @Override
    public NodeAttachment getNodeContent(ContentRef contentRef) {
        Supplier<NodeAttachment> f = () -> {
            try {
                log.debug("Searching for node {}, cppn: {}, fileName: {}, tenant: {}, schema: {}", contentRef.getUuid(), contentRef.getContentPropertyName(), contentRef.getFileName(), UserContextManager.getTenant(), UserContextManager.getContext().getDbSchema());
                return getNodeContent(contentRef.getUuid(), contentRef.getContentPropertyName(), contentRef.getFileName());
            } catch (IOException e) {
                throw new SystemException(e);
            } catch (PreconditionFailedException e) {
                throw new BadRequestException(e.getMessage());
            }
        };

        final NodeAttachment a;
        final var tenant = UserContextManager.getTenant();
        if (StringUtils.isBlank(contentRef.getTenant()) || StringUtils.equalsIgnoreCase(tenant, contentRef.getTenant())) {
            log.debug("Searching in tenant {} (current tenant: {})", contentRef.getTenant(), tenant);
            a = f.get();
        } else {
            var authorityRef = new AuthorityRef(contentRef.getIdentity() == null ? "admin" : contentRef.getIdentity(), TenantRef.valueOf(contentRef.getTenant()));
            a = TransactionService.current().doAsUser(authorityRef, f);
        }

        return a;
    }

    @Override
    public NodeAttachment getNodeContent(String uuid, String contentPropertyName) throws IOException {
        return getNodeContent(uuid, contentPropertyName, null);
    }

    @Override
    public NodeAttachment getNodeContent(String uuid, String contentPropertyName, String fileName) throws IOException {
        var n = nodeDAO
            .findNodeByUUID(uuid, Set.of(MapOption.DEFAULT), QueryScope.DEFAULT)
            .map(node -> permissionValidator.requirePermission(node, PermissionFlag.R))
            .orElseThrow(() -> new NotFoundException(uuid));

        return getNodeContent(n, contentPropertyName, fileName);
    }

    @Override
    public Map<String, NodeAttachment> getNodeContents(Collection<ContentRequest> inputs, Long limit) throws IOException {
        var map = inputs.stream().collect(Collectors.toMap(ContentRequest::getUuid, ContentRequest::getContentPropertyName));
        Map<String, ActiveNode> nodeMap = nodeDAO.mapNodesInUUIDs(map.keySet(), Set.of(MapOption.DEFAULT), QueryScope.DEFAULT);
        Map<String, NodeAttachment> result = new HashMap<>();
        long totalSize = 0;
        for (var node : nodeMap.values()) {
            NodeAttachment a = getNodeContent(node, map.get(node.getUuid()), null);
            result.put(node.getUuid(), a);

            if (a.getContentProperty().getSize() != null) {
                totalSize += a.getContentProperty().getSize();
                if (limit != null && totalSize > limit) {
                    throw new LimitExceededException(String.format("Limit exceeded: %d bytes", limit));
                }
            }
        }

        return result;
    }

    private NodeAttachment getNodeContent(ActiveNode n, String contentPropertyName, String fileName) throws IOException {
        return contentRetriever.retrieveContent(n.getData(), contentPropertyName, fileName);
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void addNodeContent(String uuid, ContentStream cs) {
        var input = new InputNodeRequest();
        input.getProperties().put(cs.getName(), createSingleContentOperation(cs, null, ContentOperationMode.ADD));
        updateNode(uuid, input, Set.of(HANDLE_CONTENT_PROPERTIES));
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void setNodeContent(String uuid, ContentStream cs, String currentFilename) {
        var input = new InputNodeRequest();
        input.getProperties().put(cs.getName(), StringUtils.isBlank(currentFilename) ? cs : createSingleContentOperation(cs, currentFilename, ContentOperationMode.REPLACE));
        updateNode(uuid, input, Set.of(HANDLE_CONTENT_PROPERTIES));
    }

    private SingleContentOperation createSingleContentOperation(ContentStream cs, String currentFilename, ContentOperationMode mode) {
        var co = new SingleContentOperation();
        co.setMode(mode);
        co.setCurrentFileName(currentFilename);
        co.setValue(cs);
        return co;
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void removeNodeContent(String uuid, String contentPropertyName, String fileName) {
        updateNode(uuid, (tx, node) -> {
            var cp = node.getData().getContentProperty(contentPropertyName, fileName);
            if (cp != null) {
                ModelSchema schema = modelManager.getContextModel();
                var td = schema.getFlatType(node.getTypeName(), node.getAspects());
                if (td != null && td.getMandatoryProperties().contains(cp.getName())) {
                    if (node.getData().countContentProperties(cp.getName()) < 2) {
                        throw new PreconditionFailedException(String.format("Property %s is mandatory", contentPropertyName));
                    }
                }

                node.getData().removeContentProperty(cp);
                if (cp.getContentUrl() != null && cp.getSize() != null && cp.getSize() > 0) {
                    // decrement counter of previous content url
                    nodeDAO.decrementContentRef(node.getTenant(), cp.getContentUrl());
                }
            } else {
                throw new PreconditionFailedException(String.format(
                    "Unable to find content property %s having filename %s in node %s (tenant %s)",
                    contentPropertyName, fileName, uuid, UserContextManager.getTenant()));
            }
        });
    }

    private void updateNode(String uuid, @NotNull BiConsumer<ApplicationTransaction, ActiveNode> consumer) {
        TransactionService.current().perform(tx -> {
            var node = simpleNodeAccessManager.getNode(uuid, PermissionFlag.W);
            consumer.accept(tx, node);
            simpleNodeAccessManager.updateNode(tx, node);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .priorityUUIDs(Set.of(uuid))
                .build();
        });
    }

    private ContentProperty createContentProperty(String tenant, ContentDescriptor descriptor, final InputStream stream) {
        try (stream) {
            ContentProperty cp = new ContentProperty();
            cp.setName(descriptor.getName());
            cp.setFileName(descriptor.getFileName());
            cp.setEncoding(descriptor.getEncoding());

            if (StringUtils.isNotBlank(descriptor.getMimetype())) {
                try {
                    var mimetypes = Arrays.stream(descriptor.getMimetype().split(", ")).toList();
                    for (String mimetype : mimetypes) {
                        MediaType.valueOf(mimetype);
                    }
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid mime type " + descriptor.getMimetype() + ": " + e.getMessage());
                }

                cp.setMimetype(descriptor.getMimetype());
            }

            // format example contentUrl=store://2012/3/23/16/46/60b73f1b-74ff-11e1-aeda-b7ce474e1849.bin
            var cal = Calendar.getInstance();
            var store = UserContextManager.getTenantData()
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
                UUID.randomUUID()
            );
            cp.setContentUrl(contentUrl);

            long size = contentStoreManager.writeStream(contentUrl, stream);
            log.debug("size = {}", size);
            cp.setSize(size);

            return cp;
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public List<NodeItem> listNodeMetadata(Collection<String> uuids, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, QueryScope scope) {
        long t0 = System.currentTimeMillis();
        var nodeMap = nodeDAO.mapNodesInUUIDs(uuids, optionSet, scope);
        log.debug("Got {} nodes in {} millis", uuids.size(), (System.currentTimeMillis() - t0));

        return uuids.stream()
            .map(nodeMap::get)
            .filter(Objects::nonNull)
            .map(node -> map(node, optionSet, filterPropertyNames, locale, optionSet.contains(ACL)))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public List<NodePathItem> listNodePaths(String uuid) {
        return Optional.of(simpleNodeAccessManager.getNode(uuid, PermissionFlag.R))
            .map(ActiveNode::getPaths)
            .map(paths -> paths.stream()
                .map(x -> nodeMapper.map(x))
                .collect(Collectors.toList())
            )
            .orElseThrow(() -> new NotFoundException(uuid));
    }

    @Override
    public String createNode(LinkedInputNodeRequest input) {
        return createNode(input, null);
    }

    String createNode(LinkedInputNodeRequest input, BiConsumer<ApplicationTransaction, ActiveNode> f) {
        return TransactionService.current().perform(tx -> {
            var node = createNode(tx, input);
            if (f != null) {
                f.accept(tx, node);
            }
            return PerformResult.<String>builder()
                .result(node.getUuid())
                .mode(PerformResult.Mode.WITHIN_TX)
                .count(1)
                .priorityUUIDs(Set.of(node.getUuid()))
                .build();
        });
    }

    @Override
    public List<String> createNodes(List<LinkedInputNodeRequest> input) {
        return TransactionService.current().perform(tx -> {
            List<ActiveNode> nodes = input.stream()
                .map(n -> createNode(tx, n))
                .toList();

            var uuids = nodes.stream()
                .map(ActiveNode::getUuid)
                .toList();

            return PerformResult.<List<String>>builder()
                .result(uuids)
                .mode(PerformResult.Mode.WITHIN_TX)
                .count(nodes.size())
                .priorityUUIDs(new HashSet<>(uuids))
                .build();
        });
    }

    @SuppressWarnings("ALL")
    ActiveNode createNode(ApplicationTransaction tx, LinkedInputNodeRequest input) {
        if (input.getAssociations().isEmpty() && !UserContextManager.getTenantData().map(TenantData::isNoPathSupported).orElse(false)) {
            throw new BadRequestException("No association specified");
        }

        if (Optional.ofNullable(input.getUnmanagedSgID()).isPresent() && input.getPermissionsDescriptor() != null) {
            throw new BadRequestException("Unmanaged Security Group and ACL both cannot be set");
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        ActiveNode node = new ActiveNode();
        node.setTenant(tenantRef.toString());
        node.setUuid(UUID.randomUUID().toString());
        node.setTypeName(input.getTypeName());

        final SecurityGroup sg;
        if (Optional.ofNullable(input.getUnmanagedSgID()).isPresent()) {
            sg = input.getUnmanagedSgID()
                .map(sgId -> {
                    TransactionService.current().options().disableWithInTxMode();
                    return aclDAO.findSecurityGroup(sgId, false)
                        .filter(s -> !s.isManaged())
                        .orElseThrow(() -> new NotFoundException("Unable to find unmanaged SG " + sgId));
                })
                .orElse(null);
        } else {
            sg = aclDAO.createManagedSG(tx, input.getPermissionsDescriptor());
        }
        node.setSecurityGroup(sg);

        if (input.getAspects().contains(ASPECT_ECMSYS_EPHEMERAL) || isGeneratedRendition(input)) {
            for (var link : input.getAssociations()) {
                if (link.getName() == null) {
                    link.setName("cm:" + node.getUuid());
                }
            }
        }

        // handle simplified copy from external source
        if (input.getCopyStreamFrom() != null) {
            var ecd = new ExternalContentDescriptor();
            ecd.copyFrom(input.getCopyStreamFrom().getTarget());

            var source = new ExternalContentDescriptor.ExternalSource();
            source.setRef(input.getCopyStreamFrom());
            source.setUri(input.getCopyStreamFrom().getUri());
            ecd.setSource(source);

            input.getProperties().put(Optional.ofNullable(ecd.getName()).orElse(CM_CONTENT), ecd);
        }

        var contentPropertyNames = new HashSet<String>();
        var optionSet = new HashSet<OperationOption>();
        optionSet.add(HANDLE_CONTENT_PROPERTIES);
        if (UserContextManager.getApiLevel() < 2) {
            optionSet.add(IGNORE_MANAGED_PROPERTIES);
        }
        fill(node, input, contentPropertyNames, optionSet);
        var properties = node.getData().getProperties();
        properties.put(CM_CREATOR, UserContextManager.getContext().getAuthorityRef().toString());
        properties.put(CM_CREATED, properties.get(CM_MODIFIED));
        if (properties.get(CM_NAME) == null) {
            properties.put(
                CM_NAME,
                input.getAssociations().stream().findFirst()
                    .map(LinkItem::getName)
                    .map(PrefixedQName::valueOf)
                    .map(QName::getLocalPart)
                    .filter(value -> {
                        var pc = propertyConverter.convertProperty(modelManager.getContextModel(), CM_NAME, value);
                        if (!nodeValidator.validateConstraints(pc)) {
                            throw new BadRequestException("Invalid name: " + value);
                        }
                        return true;
                    })
                    .orElse(node.getUuid())
            );
        }
        nodeValidator.validateMetadata(modelManager.getContextModel(), node);

        node.setTx(tx);
        node.setTransactionFlags(IndexingFlags.formatAsBinary(IndexingFlags.FULL_FLAG_MASK));
        node.setUpdatedAt(ZonedDateTime.now());
        log.debug("{} content property to process", contentPropertyNames.size());
        processContentDescriptors(node, contentPropertyNames);
        nodeDAO.createNode(node);
        linkManager.createLinks(tx, node, input.getAssociations(),
            !UserContextManager.getTenantData().map(TenantData::isNoPathSupported).orElse(false), null);
        processPostUpdate(node, input);

        if (ObjectUtils.getAsBoolean(node.getProperties().get(CM_INITIAL_VERSION), false) || ObjectUtils.getAsBoolean(node.getProperties().get(CM_AUTO_VERSION), false)) {
            versionDAO.createNodeVersion(node, null);
        }

        return node;
    }

    public void processContentDescriptors(ActiveNode node, Set<String> contentPropertyNames) {
        var properties = node.getData().getProperties();
        if (contentPropertyNames != null && !contentPropertyNames.isEmpty()) {
            // contents organized by property name
            final Map<String, Map<String, ContentProperty>> cMap = buildContentsMap(node);

            var incrementingContents = new ArrayList<ContentProperty>();
            var decrementingContents = new ArrayList<ContentProperty>();
            for (var pname : contentPropertyNames) {
                List<ContentProperty> contents = new ArrayList<>();
                var pvalue = properties.remove(pname);
                if (pvalue != null) {
                    if (pvalue instanceof Collection<?> collection) {
                        for (var item : collection) {
                            processContentItem(pname, item, node, contents);
                        }
                    } else {
                        processContentItem(pname, pvalue, node, contents);
                    }
                }

                // previous contents of a property organized by url
                var pMap = cMap.get(pname);
                if (pMap == null) {
                    pMap = new LinkedHashMap<>();
                }

                var target = new ArrayList<ContentProperty>();
                for (ContentProperty cp : contents) {
                    var previous = pMap.remove(cp.getContentUrl());
                    if (previous == null) {
                        // the content url was not present in the contents array
                        // therefore it must be added to the files table
                        incrementingContents.add(cp);
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
                                        if (!StringUtils.equals(prev.getFileName(), cp.getOp().getCurrentFileName())) {
                                            target.add(prev);
                                        } else if (!StringUtils.equals(prev.getContentUrl(), cp.getContentUrl())) {
                                            decrementingContents.add(prev);
                                        }
                                    }

                                    pMap.clear();
                                } else if (!target.isEmpty()) {
                                    var newTarget = new ArrayList<ContentProperty>();
                                    for (var prev : target) {
                                        if (!StringUtils.equals(prev.getFileName(), cp.getOp().getCurrentFileName())) {
                                            newTarget.add(prev);
                                        } else if (!StringUtils.equals(prev.getContentUrl(), cp.getContentUrl())) {
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

                // put all contents into full map
                cMap.put(pname, target.stream().collect(Collectors.toMap(ContentProperty::getContentUrl, Function.identity())));
            } // end for each content property

            nodeDAO.incrementContentRef(node.getTenant(), incrementingContents);
            nodeDAO.decrementContentRef(node.getTenant(), decrementingContents);

            node.getData().getContents().clear();
            node.getData().getContents().addAll(cMap.values().stream().flatMap(m -> m.values().stream()).toList());
        }
    }

    private Map<String, Map<String, ContentProperty>> buildContentsMap(ActiveNode node) {
        final Map<String, Map<String,ContentProperty>> cMap = new HashMap<>();
        for (ContentProperty cp : node.getData().getContents()) {
            cMap.compute(cp.getName(), (k,v) -> {
                var map = v;
                if (v == null) {
                    map = new LinkedHashMap<>();
                }

                if (StringUtils.isNotBlank(cp.getContentUrl())) {
                    map.put(cp.getContentUrl(), cp);
                }

                return map;
            });
        }
        return cMap;
    }

    private void processContentItem(String pname, Object item, ActiveNode node, final List<ContentProperty> contents) {
        if (item instanceof ContentBasicDescriptor cd) {
            ObjectUtils.add(contents, createContentDescriptor(pname, node, cd));
        } else if (item instanceof SingleContentOperation co) {
            var op = new ContentProperty.UpdateOperation();
            op.setMode(co.getMode());
            op.setCurrentFileName(co.getCurrentFileName());

            var cp = createContentDescriptor(pname, node, co.getValue());
            if (cp != null) {
                cp.setOp(op);
                contents.add(cp);
            } else if (co.getMode() == ContentOperationMode.REMOVE) {
                cp = new ContentProperty();
                cp.setName(pname);
                cp.setOp(op);
                contents.add(cp);
            }
        }
    }

    private ContentProperty createContentDescriptor(String pname, ActiveNode node, ContentBasicDescriptor cd) {
        if (cd != null) {
            if (cd.getName() == null) {
                cd.setName(pname);
            } else if (!StringUtils.equals(cd.getName(), pname)) {
                throw new BadRequestException(String.format("Content descriptor property name does not match: found '%s' instead of '%s'", cd.getName(), pname));
            }
        }

        if (cd instanceof ContentProperty cp) {
            return cp;
        } else if (cd instanceof ContentStream cs) {
            if (cs.getInputStream() != null) {
                var cp = createContentProperty(node.getTenant(), cs, cs.getInputStream());
                TransactionService.current().options().registerCreatedContentUrl(cp.getContentUrl());
                return cp;
            }
        } else if (cd instanceof ExternalContentDescriptor ecd) {
            var cc = retrieveSource(ecd);
            if (cc != null) {
                if (cc instanceof NodeAttachment a && cc.getDescriptor() instanceof ContentProperty cp && a.getStore() != null) {
                    var store = a.getStore();
                    if (store.getTenant() != null && !store.getTenant().equals(UserContextManager.getTenant())) {
                        nodeDAO.unCountContentRef(store.getDbSchema(), store.getTenant(), cp);
                        cp.setContentUrl(contentRetriever.relocate(cp.getContentUrl(), a.getStore().getPath()));
                        nodeDAO.unCountContentRef(UserContextManager.getContext().getDbSchema(), node.getTenant(), cp);
                    }

                    return cp;
                } else if (cc instanceof ContentStream cs) {
                    if (cs.getInputStream() != null) {
                        var cp = createContentProperty(node.getTenant(), cs, cs.getInputStream());
                        TransactionService.current().options().registerCreatedContentUrl(cp.getContentUrl());
                        return cp;
                    }
                }
            }
        }

        return null;
    }

    private ContentContainer retrieveSource(ExternalContentDescriptor d) {
        if (d.getSource() == null) {
            return null;
        }

        if (d.getSource().getUri() != null) {
            if (d.getSource().getRef() != null) {
                throw new BadRequestException("'uri' and 'ref' cannot be both specified in source of external content");
            }

            var uri = d.getSource().getUri();
            log.debug("Downloading source from URI '{}'", uri);
            if (StringUtils.equals(uri.getScheme(), "http") || StringUtils.equals(uri.getScheme(), "https")) {
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
                    return cs;
                } catch (IOException | InterruptedException e) {
                    throw new SystemException(e);
                }
            } else {
                throw new BadRequestException("Invalid source uri " + uri);
            }
        } else if (d.getSource().getRef() != null) {
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

    @Override
    public void deleteNode(String uuid, DeleteMode deleteMode) {
        TransactionService.current().perform(tx -> {
            var counter = new AtomicLong(0);
            linkManager.removeAllLinks(tx, uuid, deleteMode, counter, Set.of());
            return PerformResult.<Long>builder()
                .result(tx.getId())
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(uuid))
                .count(counter.get())
                .build();
        });
    }

    @Override
    public void updateNode(String uuid, InputNodeRequest input, Set<OperationOption> optionSet) {
        TransactionService.current().perform(tx -> {
            var node = nodeDAO.findNodeByUUID(uuid, Set.of(MapOption.DEFAULT), QueryScope.DEFAULT)
                .orElseThrow(() -> new NotFoundException(uuid));
            updateNode(tx, node, input, optionSet);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .priorityUUIDs(Set.of(uuid))
                .build();
        });
    }

    @Override
    public void renameNode(String uuid, String name, LinkMode mode) {
        TransactionService.current().perform(tx -> {
            var node = nodeDAO.findNodeByUUID(uuid, Set.of(MapOption.DEFAULT), QueryScope.DEFAULT)
                .orElseThrow(() -> new NotFoundException(uuid));

            var counter = new AtomicLong(0);
            linkManager.renameLinks(tx, uuid, Optional.ofNullable(mode).orElse(LinkMode.ALL), name, counter);
            if (mode == LinkMode.ALL) {
                var input = new InputNodeRequest();
                input.getProperties().put(CM_NAME, PrefixedQName.valueOf(name).getLocalPart());
                updateNode(tx, node, input, Set.of());
                counter.incrementAndGet();
            }

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(counter.get())
                .priorityUUIDs(Set.of(uuid))
                .build();
        });
    }

    @Override
    public void updateNodes(Collection<InputIdentifiedNodeRequest> inputs, Set<OperationOption> optionSet) {
        TransactionService.current().perform(tx -> {
            var uuids = inputs.stream().map(InputIdentifiedNodeRequest::getUuid).collect(Collectors.toSet());
            var nodeMap = nodeDAO.mapNodesInUUIDs(uuids, Set.of(MapOption.DEFAULT), QueryScope.DEFAULT);
            Collection<String> missingUUIDs = CollectionUtils.diff(uuids, nodeMap.keySet());
            if (!missingUUIDs.isEmpty()) {
                throw new NotFoundException(String.join(",", missingUUIDs));
            }

            var updatedUUIDs = inputs.stream()
                .map(input -> updateNode(tx, nodeMap.get(input.getUuid()), input, optionSet))
                .collect(Collectors.toSet());

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(updatedUUIDs.size())
                .priorityUUIDs(updatedUUIDs)
                .build();
        });
    }

    String updateNode(ApplicationTransaction tx, ActiveNode node, InputNodeRequest input, Set<OperationOption> optionSet) {
        log.debug("Updating node {} with {}", node.getId(), input);
        permissionValidator.requirePermission(node, PermissionFlag.W);
        if (input.getProperties().containsKey(CM_OWNER)) {
            permissionValidator.requirePermission(node, PermissionFlag.A);
        }

        var wasVersionable = node.getAspects().contains(ASPECT_CM_VERSIONABLE);
        var contentPropertyNames = new HashSet<String>();
        fill(node, input, contentPropertyNames, optionSet);
        nodeValidator.validateMetadata(modelManager.getContextModel(), node);
        var txFlag = IndexingFlags.METADATA_FLAG;
        if (Optional.ofNullable(input.getUnmanagedSgID()).isPresent()) {
            if (node.getSecurityGroup() != null && node.getSecurityGroup().isManaged()) {
                throw new ForbiddenException("Cannot update a managed SG assigment");
            }

            permissionValidator.requirePermission(node, PermissionFlag.A);
            var sg = input.getUnmanagedSgID()
                .map(unmanagedSgId -> aclDAO
                    .findSecurityGroup(unmanagedSgId, false)
                    .filter(s -> {
                        if (s.isManaged()) {
                            throw new ForbiddenException("Cannot assign a managed SG" + unmanagedSgId + "to an unmanaged node");
                        }

                        return true;
                    })
                    .orElseThrow(() -> new PreconditionFailedException("Cannot find unmanaged SG " + unmanagedSgId)))
                .orElse(null);

            node.setSecurityGroup(sg);
            txFlag |= IndexingFlags.SG_FLAG;
        }

        processContentDescriptors(node, contentPropertyNames);
        var initialVersion = !wasVersionable && ObjectUtils.getAsBoolean(node.getProperties().get(CM_INITIAL_VERSION), false);
        simpleNodeAccessManager.updateNode(tx, node, txFlag, initialVersion);
        processPostUpdate(node, input);
        return node.getUuid();
    }

    private void processPostUpdate(ActiveNode node, InputNodeRequest input) {
        // if expirable schedule delete with mode = expired
        var pd = new PropertyDescriptor();
        pd.setName(Constants.PROP_ECMSYS_EXPIRES_AT);
        pd.setType(PropertyConverter.TYPE_DATETIME);
        if (propertyConverter.convertPropertyValue(pd, input.getProperties().get(Constants.PROP_ECMSYS_EXPIRES_AT)) instanceof ZonedDateTime expiresAt) {
            long delay = 0;
            if (expiresAt.isAfter(ZonedDateTime.now())) {
                delay = expiresAt.toInstant().toEpochMilli() - System.currentTimeMillis();
            }

            var deleteOp = new NodeOperation();
            deleteOp.setUuid(node.getUuid());
            deleteOp.setOp(DELETE);
            var operand = new NodeOperation.DeleteOperand();
            operand.setMode(DeleteMode.EXPIRED);
            deleteOp.setOperand(operand);

            multipleNodeOperationService.performOperations(List.of(deleteOp), null, OperationMode.ASYNC, delay);
        }
    }

    private AspectDescriptor getAspect(ModelSchema schema, String name) {
        if (StringUtils.isBlank(name)) {
            log.warn("null or blank aspect ignored");
            return null;
        }

        AspectDescriptor ad = schema.getAspect(name);
        if (ad == null) {
            var err = String.format("Missing aspect %s in the model", name);
            if (StringUtils.startsWith(name, "ecm-sys:")) {
                log.warn(err);
                return null;
            }

            throw new BadDataException(err);
        }
        return ad;
    }

    private void fill(ActiveNode node, InputNodeRequest input, Set<String> contentPropertyNames, Set<OperationOption> optionSet) {
        ModelSchema schema = modelManager.getContextModel();
        if (UserContextManager.getApiLevel() >= 2 && input.getTypeName() != null) {
            node.setTypeName(input.getTypeName());
        }

        Set<String> aspects = node.getData().getAspects();
        if (!input.getAspects().isEmpty()) {
            var preservedAspects = aspects.stream()
                .filter(Objects::nonNull)
                .filter(name -> !input.getAspects().contains(name))
                .filter(name -> !UserContextManager.getTenantData().map(TenantData::getImplicitAspects).orElse(Set.of()).contains(name))
                .filter(name -> managedAspects.contains(name) || name.startsWith("ecm-sys:") || name.startsWith("sys:"))
                .toList();

            aspects.clear();
            aspects.addAll(preservedAspects);
            aspects.addAll(input.getAspects()
                .stream()
                .filter(name -> !UserContextManager.getTenantData().map(TenantData::getImplicitAspects).orElse(Set.of()).contains(name))
                .filter(name -> !StringUtils.equals(name, ASPECT_ECMSYS_ASYNCREQUIRED))
                .map(name -> getAspect(schema, name))
                .filter(Objects::nonNull)
                .map(TypedInterfaceDescriptor::getName)
                .toList());
        }

        input.getAspectOperations().forEach((a, op) -> {
            switch (op) {
                case ADD -> aspects.add(a);
                case REMOVE -> aspects.remove(a);
            }
        });

        if (input.getProperties().containsKey(CM_INITIAL_VERSION) || input.getProperties().containsKey(CM_AUTO_VERSION)) {
            input.getAspects().add(ASPECT_CM_VERSIONABLE);
        }

        aspects.add(ASPECT_CM_AUDITABLE);
        aspects.add(ASPECT_SYS_REFERENCEABLE);
        aspects.remove(ASPECT_SYS_ARCHIVED);

        var allowManagedProperties = optionSet.contains(ALLOW_MANAGED_PROPERTIES);
        var ignoreManagedProperties = optionSet.contains(IGNORE_MANAGED_PROPERTIES);
        var handleContentProperties = optionSet.contains(HANDLE_CONTENT_PROPERTIES);
        Map<String, Object> properties = node.getData().getProperties();
        input.getProperties().entrySet()
            .stream()
            .filter(entry -> !generatedPropertySet.contains(entry.getKey()))
            .map(entry -> propertyConverter.convertProperty(schema, entry.getKey(), entry.getValue()))
            .filter(Objects::nonNull)
            .filter(pc -> handleContentProperties || !StringUtils.equals(pc.getDescriptor().getType(), TYPE_CONTENT))
            .filter(pc -> nodeValidator.validateConstraints(pc))
            .forEach(pc -> {
                var value = propertyConverter.serializePropertyValue(pc.getDescriptor(), pc.getValue());
                var name = pc.getDescriptor().getName();

                if (StringUtils.equals(pc.getDescriptor().getType(), TYPE_CONTENT)) {
                    if (contentPropertyNames != null) {
                        contentPropertyNames.add(name);
                    }
                } else if (!allowManagedProperties) {
                    if (pc.getDescriptor().isManaged()) {
                        if (!ignoreManagedProperties) {
                            if (properties.containsKey(name)) {
                                var previous = Optional.ofNullable(propertyConverter.convertProperty(pc.getDescriptor(), properties.get(name))).map(PropertyContainer::getValue).orElse(null);
                                if (!Objects.equals(pc.getValue(), previous)) {
                                    throw new BadRequestException(String.format("Managed property %s cannot be changed: current value is '%s', provided '%s'", name, previous, pc.getValue()));
                                }
                            } else {
                                throw new BadRequestException("Managed property cannot be added: " + name);
                            }
                        }

                        return;
                    } else if (pc.getDescriptor().isImmutable() && properties.containsKey(name)) {
                        throw new ForbiddenException("Immutable property cannot be changed: " + name);
                    }
                }

                if (StringUtils.equals(name, CM_OWNER)) {
                    permissionValidator.requireOwnership(node);
                }

                if (value == null) {
                    properties.remove(name);
                } else if (value instanceof Optional<?> optional && optional.isEmpty()) {
                    properties.put(name, null);
                } else {
                    if (value instanceof Optional<?> optional) {
                        value = optional.get();
                    } else if (value instanceof PropertyValueOperation pvo) {
                        if (pc.getDescriptor().isMultiple()) {
                            final var list = new ArrayList<>();
                            var previous = Optional.ofNullable(propertyConverter.convertProperty(pc.getDescriptor(), properties.get(name))).map(PropertyContainer::getValue).orElse(null);
                            if (previous instanceof Collection<?> c) {
                                list.addAll(c);
                            } else if (previous != null) {
                                list.add(previous);
                            }

                            mergeList(list, pvo);
                            value = list;
                        } else {
                            throw new BadRequestException("Unsupported delta operation on single value property " + name);
                        }
                    }

                    properties.put(name, value);
                }
            });

        // audit
        properties.put(CM_MODIFIER, UserContextManager.getContext().getAuthorityRef().toString());
        properties.put(CM_MODIFIED, DateISO8601Utils.dateFormat.format(ZonedDateTime.now()));
        properties.remove(PROP_SYS_ARCHIVEDBY);
        properties.remove(PROP_SYS_ARCHIVEDDATE);
    }

    private void mergeList(final List<Object> list, PropertyValueOperation pvo) {
        switch (pvo.getOp()) {
            case "pop":
                if (!list.isEmpty()) {
                    list.remove(0);
                }
                break;

            case "remove":
                if (pvo.getValue() instanceof Collection<?> c) {
                    list.removeAll(c);
                } else {
                    list.remove(pvo.getValue());
                }
                break;

            case "put": {
                Consumer<Object> consumer = (o) -> {
                    if (list.stream().filter(x -> x.equals(o)).findFirst().isEmpty()) {
                        list.add(o);
                    }
                };

                if (pvo.getValue() instanceof Collection<?> c) {
                    for (Object o : c) {
                        consumer.accept(o);
                    }
                } else {
                    consumer.accept(pvo.getValue());
                }
                break;
            }

            case "append":
            case "add":
                if (pvo.getValue() instanceof Collection<?> c) {
                    list.addAll(c);
                } else {
                    list.add(pvo.getValue());
                }
                break;

            case "push":
            case "insert":
                if (pvo.getValue() instanceof Collection<?> c) {
                    list.addAll(0, c);
                } else {
                    list.add(0, pvo.getValue());
                }
                break;

            case "multi":
                if (pvo.getValue() instanceof Collection<?> c) {
                    for (Object o : c) {
                        if (o instanceof PropertyValueOperation x) {
                            mergeList(list, x);
                        }
                    }
                }
                break;

            default:
                break;
        }
    }

    @Override
    public String copyNode(String uuid, LinkItemRequest destination, boolean includeChildren, boolean includeAssociations, CopyMode copyMode) {
        return TransactionService.current().perform(tx -> {
            AtomicLong counter = new AtomicLong(0);
            var copiedNode = copyNode(tx, uuid, destination, includeChildren, includeAssociations, copyMode, counter);
            return PerformResult.<String>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(counter.get())
                .priorityUUIDs(Set.of(copiedNode.getUuid()))
                .result(copiedNode.getUuid())
                .build();
        });
    }

    ActiveNode copyNode(ApplicationTransaction tx, String uuid, LinkItemRequest destination, boolean includeChildren, boolean includeAssociations, CopyMode copyMode, AtomicLong counter) {
        var nodeMap = linkManager.retrieveRelatedNodes(uuid, List.of(destination), false, Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS));
        var node = Optional.ofNullable(nodeMap.get(uuid)).orElseThrow(() -> new NotFoundException("source: " + uuid));

        if (nodeMap.size() == 1 && Objects.equals(node, nodeMap.get(uuid))) {
            throw new BadRequestException("Cannot copy a node under itself");
        }
        permissionValidator.requirePermission(node, PermissionFlag.R);

        nodeMap.values().stream()
            .filter(n -> !Objects.equals(n, node))
            .forEach(n -> n.getPaths().stream()
                .map(NodePath::getPath)
                .forEach(p -> Arrays.stream(p.split(":"))
                    .filter(StringUtils::isNotBlank)
                    .map(Long::parseLong)
                    .filter(l -> Objects.equals(l, node.getId()))
                    .findFirst()
                    .ifPresent(e -> {
                        throw new BadRequestException(String.format("Cannot create a circular relationship: %s is an ancestor of %s", node.getUuid(), n.getUuid()));
                    })
                )
            );

        node.getParents().stream().filter(Association::isHard).findFirst().ifPresent(a -> {
            if (destination.getTypeName() == null) {
                destination.setTypeName(a.getTypeName());
            }

            if (destination.getName() == null) {
                var sameNameAssociation = associationDAO.findAssociationsWithSameName(destination.getVertexUUID(), a.getName());
                if (sameNameAssociation != null && sameNameAssociation.getName() != null) {
                    int dot = a.getName().lastIndexOf('.');
                    var underscore = a.getName().indexOf('_');
                    var regex = "^(.*)(_[0-9]{8}T[0-9]{9})(.*)$";
                    if (underscore > 0 && ObjectUtils.takeRegexPart(a.getName(), regex, 2) != null) {
                        destination.setName(a.getName().substring(0, underscore) + "_" + dateFormatter.format(ZonedDateTime.now()) + (dot > 0 && underscore < dot ? a.getName().substring(dot) : ""));
                    } else {
                        destination.setName((dot < 0 ? a.getName() : a.getName().substring(0, dot)) + "_" + dateFormatter.format(ZonedDateTime.now()) + (dot > 0 ? a.getName().substring(dot) : ""));
                    }
                } else {
                    destination.setName(a.getName());
                }
            }
        });

        var duplicatedNode = node.copy();

        var sg = duplicatedNode.getSecurityGroup();
        if (sg != null && sg.isManaged()) {
            sg.setId(null);
            sg.setName(null);
            sg.setUuid(null);
            sg.setTx(tx);
            nodeDAO.createSG(sg);
        }

        duplicatedNode.getProperties().remove(PROP_ECMSYS_SHARED_LINKS);
        duplicatedNode.getAspects().remove(ASPECT_ECMSYS_SHARED);
        duplicatedNode.getProperties().put(PROP_CM_COPIED_NODE, node.getUuid());
        if (destination.getName() != null) {
            duplicatedNode.getProperties().put(CM_NAME, PrefixedQName.valueOf(destination.getName()).getLocalPart());
        }
        duplicatedNode.getAspects().add(ASPECT_COPIED_NODE);
        duplicatedNode.setTx(tx);
        duplicatedNode.setTransactionFlags(IndexingFlags.formatAsBinary(IndexingFlags.FULL_FLAG_MASK));
        if (duplicatedNode.getData().getInternals() == null) {
            duplicatedNode.getData().setInternals(new HashMap<>());
        }
        duplicatedNode.getData().getInternals().put("ecm-sys:source-DBID", node.getId());
        nodeDAO.createNode(duplicatedNode);
        linkManager.createAssociations(tx, duplicatedNode, nodeMap, List.of(destination), counter);

        if (includeChildren) {
            nodeDAO.copySubNodes(tx, node, duplicatedNode, counter);
            nodeDAO.incrementContentRefCounterForTX(tx);
            int n = nodeDAO.setTxDescendingOfNode(tx, node, true);
            if (copyMode != null && !Objects.equals(copyMode, CopyMode.NAME)) {
                nodeDAO.setNameOfDescendingNodes(tx, duplicatedNode, copyMode);
            }
            log.debug("{} involved nodes set with tx {} including children", n, tx.getId());
        } else {
            nodeDAO.incrementContentRefCounterForTX(tx);
            if (includeAssociations) {
                associationDAO.copyChildrenAssociations(node, duplicatedNode);
                int n = nodeDAO.setTxDescendingOfNode(tx, node, false);
                log.debug("{} involved nodes set with tx {}", n, tx.getId());
            }
        }

        if (includeChildren || includeAssociations) {
            // rebuild paths where tx excluding new created main duplicated node
            var rootId = node.getPaths().stream()
                .findFirst()
                .map(NodePath::getPath)
                .stream()
                .flatMap(p -> Arrays.stream(p.split(":")))
                .filter(StringUtils::isNotBlank)
                .map(Long::parseLong)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unable to determine root from node " + node.getId()));

            associationDAO.rebuildPathsWhereNodeTx(tx.getId(), rootId, List.of(duplicatedNode.getId()));
        }

        int n = versionDAO.createNodeVersions(tx);
        log.debug("{} node versions created for auto or initial version", n);
        return duplicatedNode;
    }

    private boolean isGeneratedRendition(LinkedInputNodeRequest input) {
        Optional<LinkItemRequest> nameFound = input.getAssociations().stream().filter(a -> a.getName() != null).findFirst();
        return nameFound.isEmpty() &&
            (input.getAspects().contains(ASPECT_ECMSYS_RENDITION) || (input.getAspectOperations().containsKey(ASPECT_ECMSYS_RENDITION) && Objects.equals(input.getAspectOperations().get(ASPECT_ECMSYS_RENDITION), ADD)))
            && input.getProperties().containsKey(PROP_ECMSYS_GENERATED)
            && Objects.equals(input.getProperties().get(PROP_ECMSYS_GENERATED), true);
    }
}
