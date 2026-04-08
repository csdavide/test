package it.doqui.libra.librabl.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.libra.librabl.application.model.graph.*;
import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.application.mappers.PropertyConverter;
import it.doqui.libra.librabl.application.ports.in.ContentUseCase;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.application.mappers.AssociationMapper;
import it.doqui.libra.librabl.domain.model.files.*;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.graph.*;
import it.doqui.libra.librabl.domain.model.schema.PropertyContainer;
import it.doqui.libra.librabl.domain.policy.*;
import it.doqui.libra.librabl.domain.model.schema.AspectDescriptor;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.model.schema.PropertyDescriptor;
import it.doqui.libra.librabl.domain.model.schema.TypedInterfaceDescriptor;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.Association;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.NodePath;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.SecurityGroup;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.association.EdgeItem;
import it.doqui.libra.librabl.application.model.association.LinkItem;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.application.model.jobs.requests.DeleteJobRequest;
import it.doqui.libra.librabl.application.model.properties.ExternalContentDescriptor;
import it.doqui.libra.librabl.application.model.properties.PropertyObject;
import it.doqui.libra.librabl.application.model.properties.PropertyValueOperation;
import it.doqui.libra.librabl.application.model.query.QueryParameters;
import it.doqui.libra.librabl.application.model.statements.RenameStatement;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.cxf.common.util.CollectionUtils;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.TYPE_CONTENT;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.TYPE_DATETIME;
import static it.doqui.libra.librabl.domain.policy.MapOption.*;
import static it.doqui.libra.librabl.application.model.graph.InputNodeRequest.AspectOperation.ADD;
import static it.doqui.libra.librabl.domain.policy.OperationOption.*;

@ApplicationScoped
@Slf4j
public class NodeManager implements NodeUseCase, ContentUseCase {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS");

    @Inject
    NodeMapper nodeMapper;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    ModelManagerPort modelManager;

    @Inject
    PropertyConverter propertyConverter;

    @Inject
    NodeValidator nodeValidator;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

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
    JobUseCase jobService;

    @Inject
    VersionDAO versionDAO;

    @Inject
    AttachmentHelper contentRetriever;

    @Inject
    DataStreamHandler dataStreamHandler;

    @Inject
    AssociationMapper associationMapper;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    final Set<String> generatedPropertySet = Set.of("sys:node-dbid", "sys:node-uuid", "sys:store-protocol", "sys:store-identifier", "ecm-sys:version", "ecm-sys:dataModifica");
    final Set<String> managedAspects = Set.of(ASPECT_CM_AUDITABLE,ASPECT_CM_WORKINGCOPY,ASPECT_COPIED_NODE);

    @Override
    public Optional<NodeItem> getNodeMetadata(Vertex vertex, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(optionSet)
                .scope(QueryScope.DEFAULT)
                .build();
        var n = nodeLoaderDAO.getNode(vertex, qc);
        if (n.isEmpty()) {
            if (optionSet.contains(CHECK_ARCHIVE) && vertex.getType() != VertexType.PATH) {
                archiveDAO.getNode(vertex)
                    .map(an -> {
                        if (an.isNodeUndefined()) {
                            throw new ForbiddenException("Permission " + PermissionFlag.R + " is required on node " + vertex.getValue());
                        }
                        return an;
                    })
                    .ifPresent(archivedNode -> {
                        var e = new NotFoundException(vertex.toString());
                        e.getDetailMap().put("uuid", archivedNode.getUuid());
                        e.getDetailMap().put("nodeid", "" + archivedNode.getId());
                        e.getDetailMap().put("archived", "true");
                        throw e;
                    });
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
    public NodeAttachment getNodeContent(ContentRef contentRef) {
        return dataStreamHandler.getNodeContent(contentRef);
    }

    @Override
    public NodeAttachment getNodeContent(Vertex vertex, URI contentUrl) throws IOException {
        var n = getNode(vertex);
        var fd = n.getContent(contentUrl).orElseThrow(PreconditionFailedException::new);
        return contentRetriever.attachment(n, fd);
    }

    @Override
    public NodeAttachment getNodeContent(Vertex vertex, ContentReferenceable ref) throws IOException {
        var n = getNode(vertex);
        var fd = n.getContent(ref).orElseThrow(PreconditionFailedException::new);
        return contentRetriever.attachment(n, fd);
    }

    private GraphNode getNode(Vertex vertex) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT))
                .scope(QueryScope.DEFAULT)
                .build();

        return nodeLoaderDAO
                .getNode(vertex, qc)
                .map(node -> permissionValidator.requirePermission(node, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(vertex.toString()));
    }

    @Override
    public Map<String, NodeAttachment> getNodeContents(Collection<ContentRequest> inputs, Long limit) throws IOException {
        var map = inputs.stream().collect(Collectors.toMap(ContentRequest::getUuid, ContentRequest::getContentPropertyName));
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .build();
        var r = nodeLoaderDAO.lookupNodes(map.keySet().stream().map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
        Map<String, NodeAttachment> result = new HashMap<>();
        long totalSize = 0;
        for (var node : r.uuidMap().values()) {
            var c = node.getContent(ContentReferenceable.of(map.get(node.getUuid()))).orElseThrow(PreconditionFailedException::new);
            var a = contentRetriever.attachment(node.getData(), c);
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

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void addNodeContent(String uuid, ContentStream cs) {
        var input = new InputNodeRequest();
        input.getProperties().put(cs.getName(), wrapContentOperation(cs, null, ContentOperationMode.ADD));
        updateNode(uuid, input, Set.of(HANDLE_CONTENT_PROPERTIES));
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void setNodeContent(String uuid, ContentStream cs, String currentFilename) {
        var input = new InputNodeRequest();
        input.getProperties().put(cs.getName(), StringUtils.isBlank(currentFilename) ? cs : wrapContentOperation(cs, currentFilename, ContentOperationMode.REPLACE));
        updateNode(uuid, input, Set.of(HANDLE_CONTENT_PROPERTIES));
    }

    private PropertyObject wrapContentOperation(ContentStream cs, String currentFilename, ContentOperationMode mode) {
        var pvo = new PropertyValueOperation();
        pvo.setOp(
                switch (mode) {
                    case ADD -> PropertyValueOperation.PropertyValueOperationType.ADD;
                    case REPLACE -> PropertyValueOperation.PropertyValueOperationType.PUT;
                    case REMOVE -> PropertyValueOperation.PropertyValueOperationType.REMOVE;
                }
        );
        pvo.setKey(currentFilename);
        pvo.setValue(cs);
        return pvo;
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void removeNodeContent(String uuid, String contentPropertyName, String fileName) {
        updateNode(uuid, (tx, node) -> {
            var cp = node.getData().getFileData(contentPropertyName, fileName);
            if (cp != null) {
                ModelSchema schema = modelManager.getContextModel();
                var td = schema.getFlatType(node.getTypeName(), node.getAspects());
                if (td != null && td.getMandatoryProperties().contains(cp.getName())) {
                    if (node.getData().countFileWithPropertyName(cp.getName()) < 2) {
                        throw new PreconditionFailedException(String.format("Property %s is mandatory", contentPropertyName));
                    }
                }

                node.getData().removeFileData(cp);
                if (cp.getContentUrl() != null && cp.getSize() != null && cp.getSize() > 0) {
                    // decrement counter of previous content url
                    nodeDAO.decrementContentRef(node.getTenant(), cp.getContentUrl());
                }
            } else {
                throw new PreconditionFailedException(String.format(
                    "Unable to find content property %s having filename %s in node %s (tenant %s)",
                    contentPropertyName, fileName, uuid, sessionContext.getTenant()));
            }
        });
    }

    private void updateNode(String uuid, @NotNull BiConsumer<ApplicationTransaction, ActiveNode> consumer) {
        transactionManagerPort.perform(tx -> {
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

    @Override
    public List<NodeItem> listNodeMetadata(Collection<String> uuids, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, QueryScope scope) {
        long t0 = System.currentTimeMillis();
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(optionSet)
                .scope(scope)
                .build();
        var r = nodeLoaderDAO.lookupNodes(uuids.stream().map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
        log.debug("Got {} nodes in {} millis", uuids.size(), (System.currentTimeMillis() - t0));

        return uuids.stream()
            .map(r.uuidMap()::get)
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
    public String createOrUpdateNode(@NotNull LinkedInputNodeRequest input, @NotNull Set<OperationOption> optionSet) {
        return transactionManagerPort.perform(tx -> {
            var parentLink = input.getLink();
            if (parentLink == null) {
                parentLink = input.getAssociations().stream()
                        .filter(EdgeItem::isHard)
                        .filter(a -> a.getRelationship() == null || a.getRelationship() == RelationshipKind.PARENT)
                        .map(link -> associationMapper.mapLinkItemRequest(link))
                        .findFirst()
                        .orElseThrow(() -> new PreconditionFailedException("No parent association"));
            }

            ActiveNode node = null;
            if (optionSet.contains(FAIL_IF_PATH_EXISTS)) {
                node = createNode(tx, input, optionSet);
            } else {
                int retry = 2;
                while (retry > 0) {
                    retry--;
                    try {
                        node = createOrUpdateNode(tx, parentLink, input, optionSet);
                    } catch (ConflictException e) {
                        if (retry < 1) {
                            throw e;
                        }
                        log.warn(e.getMessage());
                    }
                }
            }

            assert node != null;
            var uuid = node.getUuid();
            return PerformResult.<String>builder()
                .result(uuid)
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .priorityUUIDs(Set.of(uuid))
                .build();
        });
    }

    private ActiveNode createOrUpdateNode(ApplicationTransaction tx, ParentLink parentLink, LinkedInputNodeRequest input, Set<OperationOption> optionSet) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(PATHS))
                .scope(QueryScope.UPDATE)
                .build();
        return nodeLoaderDAO.getNode(parentLink, qc)
                .map(n -> {
                    log.debug("Found existing node {}", n.getUuid());
                    updateNode(tx, n, input, optionSet);
                    return n;
                })
                .orElseGet(() -> createNode(tx, input, optionSet));
    }

    @Override
    public String createNode(LinkedInputNodeRequest input) {
        return createNode(input, Set.of(), null);
    }

    //TODO: createNode with functions pre-tx, i.e. for verifying before creating node
    String createNode(LinkedInputNodeRequest input, Set<OperationOption> optionSet, BiConsumer<ApplicationTransaction, ActiveNode> f) {
        var cSet = dataStreamHandler.downloadStreams(input);
        return transactionManagerPort.perform(tx -> {
            var options = transactionManagerPort.options();
            cSet.stream().map(ContentProperty::getContentUrl).filter(Objects::nonNull).forEach(options::registerCreatedContentUrl);
            var node = createNode(tx, input, optionSet);
            if (f != null) {
                f.accept(tx, node);
            }
            return PerformResult.<String>builder()
                .result(node.getUuid())
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .priorityUUIDs(Set.of(node.getUuid()))
                .build();
        });
    }

    @Override
    public List<String> createNodes(List<LinkedInputNodeRequest> input, @NotNull Set<OperationOption> optionSet) {
        return transactionManagerPort.perform(tx -> {
            List<ActiveNode> nodes = input.stream()
                .map(n -> createNode(tx, n, optionSet))
                .toList();

            var uuids = nodes.stream()
                .map(ActiveNode::getUuid)
                .toList();

            return PerformResult.<List<String>>builder()
                .result(uuids)
                .mode(PerformResult.Mode.SYNC)
                .count(nodes.size())
                .priorityUUIDs(new HashSet<>(uuids))
                .build();
        });
    }

    @Override
    public String createNode(LinkedInputNodeRequest input, Set<OperationOption> optionSet) {
        return createNode(input, optionSet, null);
    }

    @SuppressWarnings("ALL")
    ActiveNode createNode(ApplicationTransaction tx, LinkedInputNodeRequest input, Set<OperationOption> additionalOptionSet) {
        boolean noPathSupported = sessionContext.getTenantData().map(TenantData::isNoPathSupported).orElse(false);
        if (!noPathSupported) {
            if (input.getLink() != null) {
                if (input.getLink().getBindingType() != null && input.getLink().getBindingType() != BindingType.HARD) {
                    throw new BadRequestException("Hard parent association is required");
                }
            } else {
                if (input.getAssociations().isEmpty()) {
                    throw new BadRequestException("No association specified");
                }

                if (input.getAssociations().stream().noneMatch(a -> a.getRelationship() == RelationshipKind.PARENT && a.isHard())) {
                    throw new BadRequestException("An hard parent association is required");
                }
            }
        }

        if (Optional.ofNullable(input.getUnmanagedSgID()).isPresent() && input.getPermissionsDescriptor() != null) {
            throw new BadRequestException("Unmanaged Security Group and ACL both cannot be set");
        }

        final TenantRef tenantRef = sessionContext.getUserContext().getTenantRef();
        ActiveNode node = new ActiveNode();
        node.setTenant(tenantRef.toString());
        node.setUuid(UuidCreator.getTimeOrderedEpoch().toString());
        node.setTypeName(input.getTypeName());

        final SecurityGroup sg;
        if (Optional.ofNullable(input.getUnmanagedSgID()).isPresent()) {
            sg = input.getUnmanagedSgID()
                .map(sgId -> {
                    transactionManagerPort.options().disableWithInTxMode();
                    return aclDAO.findSecurityGroup(sgId, false)
                        .filter(s -> !s.isManaged())
                        .orElseThrow(() -> new NotFoundException("Unable to find unmanaged SG " + sgId));
                })
                .orElse(null);
        } else {
            sg = aclDAO.createManagedSG(tx, input.getPermissionsDescriptor());
        }
        node.setSecurityGroup(sg);

        //TODO: rivedere usando parentLink
        if (input.getAspects().contains(ASPECT_ECMSYS_EPHEMERAL) || isGeneratedRendition(input)) {
            if (input.getLink() != null) {
                if (input.getLink().getName() == null) {
                    input.getLink().setName("cm:" + node.getUuid());
                }
            }
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
        var optionSet = new HashSet<OperationOption>(additionalOptionSet);
        optionSet.add(HANDLE_CONTENT_PROPERTIES);
        if (sessionContext.getApiLevel() < 2) {
            optionSet.add(IGNORE_MANAGED_PROPERTIES);
        }
        fill(node, input, contentPropertyNames, optionSet);
        var properties = node.getData().getProperties();
        properties.put(CM_CREATOR, sessionContext.getUserContext().getAuthorityRef().toString());
        properties.put(CM_CREATED, properties.get(CM_MODIFIED));
        if (properties.get(CM_NAME) == null) {
            var name = Optional.ofNullable(input.getLink())
                    .map(ParentLink::getName)
                    .map(PrefixedQName::valueOf)
                    .map(QName::getLocalPart)
                    .orElseGet(() -> input.getAssociations().stream().findFirst()
                            .map(LinkItem::getName)
                            .map(PrefixedQName::valueOf)
                            .map(QName::getLocalPart)
                            .orElse(null)
                    );

            if (name != null) {
                var pc = propertyConverter.convertProperty(modelManager.getContextModel(), CM_NAME, name);
                if (!nodeValidator.validateConstraints(pc)) {
                    throw new BadRequestException("Invalid name: " + name);
                }
            } else {
                name = node.getUuid();
            }

            properties.put(CM_NAME, name);
        }
        nodeValidator.validateMetadata(modelManager.getContextModel(), node);

        node.setTx(tx);
        node.setTransactionFlags(IndexingFlags.formatAsBinary(IndexingFlags.FULL_FLAG_MASK));
        node.setUpdatedAt(ZonedDateTime.now());
        log.debug("{} content property to process", contentPropertyNames.size());
        dataStreamHandler.processContentDescriptors(node, contentPropertyNames, optionSet);
        nodeDAO.createNode(node);
        if (input.getLink() != null) {
            input.getLink().setBindingType(BindingType.HARD);
            linkManager.createParentLink(tx, node, input.getLink(), additionalOptionSet.contains(CREATE_MISSING_PARENTS), null);
        }
        for (var linkItemRequest : input.getAssociations()) {
            var createIfNotExists = additionalOptionSet.contains(CREATE_MISSING_PARENTS) || linkItemRequest.isCreateIfNotExists();
            linkManager.createParentLink(tx, node, associationMapper.mapLinkItemRequest(linkItemRequest), createIfNotExists, null);
        }

        processPostUpdate(node, input);

        if (ObjectUtils.getAsBoolean(node.getProperties().get(CM_INITIAL_VERSION), false) || ObjectUtils.getAsBoolean(node.getProperties().get(CM_AUTO_VERSION), false)) {
            versionDAO.createNodeVersion(node, null);
        }

        return node;
    }

    @Override
    public void deleteNode(String uuid, DeleteMode deleteMode) {
        transactionManagerPort.perform(tx -> {
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
        var cSet = dataStreamHandler.downloadStreams(input);
        transactionManagerPort.perform(tx -> {
            var options = transactionManagerPort.options();
            cSet.stream().map(ContentProperty::getContentUrl).filter(Objects::nonNull).forEach(options::registerCreatedContentUrl);
            var node = nodeLoaderDAO.getNode(
                            new Vertex(VertexType.UUID, uuid),
                            QueryContext.builder()
                                    .schema(sessionContext.getUserContext().getDbSchema())
                                    .tenant(sessionContext.getTenant())
                                    .scope(QueryScope.UPDATE)
                                    .build())
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
    public long renameNode(Vertex vertex, RenameStatement rename) {
        return transactionManagerPort.perform(tx -> {
            var counter = new AtomicLong(0);
            var scope = StringUtils.isNotBlank(rename.getPropertyName()) ? QueryScope.UPDATE : QueryScope.DEFAULT;
            var qc = QueryContext.builder()
                    .schema(sessionContext.getUserContext().getDbSchema())
                    .tenant(sessionContext.getTenant())
                    .optionSet(Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS))
                    .scope(scope)
                    .build();
            var node = nodeLoaderDAO.getNode(vertex, qc).orElseThrow(() -> new NotFoundException(vertex.toString()));
            if (StringUtils.isNotBlank(rename.getPropertyName())) {
                var name = PrefixedQName.valueOf(rename.getName()).getLocalPart();
                if (!Strings.CS.equals(ObjectUtils.getAsString(node.getProperties().get(rename.getPropertyName())), name)) {
                    var input = new InputNodeRequest();
                    input.getProperties().put(rename.getPropertyName(), name);
                    updateNode(node.getUuid(), input, Set.of());
                }
            }

            if (rename.getRenameMode() == RenameStatement.RenameMode.SPECIFIC_PARENT) {
                if (rename.getParent() == null) {
                    throw new BadRequestException("Parent association is mandatory for rename in specific parent mode");
                }

                linkManager.renameLink(tx, rename.getParent(), vertex, rename.getName(), counter);
            } else {
                LinkMode linkMode = switch (rename.getRenameMode()) {
                    case SPECIFIC_PARENT -> throw new BadRequestException("Rename in specific parent mode is not supported anymore");
                    case ALL_PARENTS -> LinkMode.ALL;
                    case FIRST_PARENT -> LinkMode.FIRST;
                    case ALL_HARD_PARENTS -> LinkMode.HARD;
                };
                linkManager.renameLinks(tx, node, linkMode, rename.getName(), counter);
            }

            long n = counter.get();
            return PerformResult.<Long>builder()
                    .mode(PerformResult.Mode.SYNC)
                    .result(n)
                    .count(n)
                    .priorityUUIDs(Set.of(node.getUuid()))
                    .build();
        });
    }

    @Override
    public long moveNode(Vertex node, ParentLink destination) {
        return transactionManagerPort.perform(tx -> {
            var counter = new AtomicLong(0);
            var a = linkManager.moveParentLink(tx, node, destination, counter);
            long n = counter.get();
            return PerformResult.<Long>builder()
                    .mode(PerformResult.Mode.SYNC)
                    .result(n)
                    .count(n)
                    .priorityUUIDs(Set.of(a.getChild().getUuid()))
                    .build();
        });
    }

    @Override
    public void updateNodes(Collection<InputIdentifiedNodeRequest> inputs, Set<OperationOption> optionSet) {
        transactionManagerPort.perform(tx -> {
            var uuids = inputs.stream().map(InputIdentifiedNodeRequest::getUuid).collect(Collectors.toSet());
            var qc = QueryContext.builder().
                    schema(sessionContext.getUserContext().getDbSchema())
                    .tenant(sessionContext.getTenant())
                    .optionSet(Set.of(MapOption.DEFAULT))
                    .scope(QueryScope.UPDATE)
                    .build();
            var r = nodeLoaderDAO.lookupNodes(uuids.stream().map(uuid -> new Vertex(VertexType.UUID, uuid)).toList(), qc);
            Collection<String> missingUUIDs = CollectionUtils.diff(uuids, r.uuidMap().keySet());
            if (!missingUUIDs.isEmpty()) {
                throw new NotFoundException(String.join(",", missingUUIDs));
            }

            var updatedUUIDs = inputs.stream()
                .map(input -> updateNode(tx, r.uuidMap().get(input.getUuid()), input, optionSet))
                .collect(Collectors.toSet());

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(updatedUUIDs.size())
                .priorityUUIDs(updatedUUIDs)
                .build();
        });
    }

    String insertTimestampInNameAssociation(Vertex vertex, String proposedName, String timestampBefore) {
        var sameNameAssociation = associationDAO.findAssociationsWithSameName(vertex, proposedName);
        if (sameNameAssociation != null && sameNameAssociation.getName() != null) {

            int indexTsBefore = proposedName.lastIndexOf(timestampBefore != null ? timestampBefore : ".");
            var timestampRegex = "^(.*)(_[0-9]{8}T[0-9]{9})(.*)$";
            var regex = ObjectUtils.takeRegexPart(proposedName, timestampRegex, 2, true);

            if (regex != null) {
                return proposedName.replace(regex, "_" + dateFormatter.format(ZonedDateTime.now()));
            } else if (indexTsBefore > 0) {
                return proposedName.substring(0, indexTsBefore) + "_" + dateFormatter.format(ZonedDateTime.now()) + proposedName.substring(indexTsBefore);
            } else {
                return proposedName + "_" + dateFormatter.format(ZonedDateTime.now());
            }
        } else {
            return proposedName;
        }
    }

    private String updateNode(ApplicationTransaction tx, ActiveNode node, InputNodeRequest input, Set<OperationOption> optionSet) {
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

        dataStreamHandler.processContentDescriptors(node, contentPropertyNames, optionSet);
        var initialVersion = !wasVersionable && ObjectUtils.getAsBoolean(node.getProperties().get(CM_INITIAL_VERSION), false);
        simpleNodeAccessManager.updateNode(tx, node, txFlag, initialVersion);
        processPostUpdate(node, input);
        return node.getUuid();
    }

    private void processPostUpdate(ActiveNode node, InputNodeRequest input) {
        // if expirable schedule delete with mode = expired
        var pd = new PropertyDescriptor();
        pd.setName(PROP_ECMSYS_EXPIRES_AT);
        pd.setType(TYPE_DATETIME);
        if (propertyConverter.convertPropertyValue(pd, input.getProperties().get(PROP_ECMSYS_EXPIRES_AT)) instanceof ZonedDateTime expiresAt) {
            long delay = 0;
            if (expiresAt.isAfter(ZonedDateTime.now())) {
                delay = expiresAt.toInstant().toEpochMilli() - System.currentTimeMillis();
            }

            var query = new QueryParameters();
            query.setUuids(List.of(node.getUuid()));
            var deleteJobRequest = new DeleteJobRequest();
            deleteJobRequest.setMode(OperationMode.ASYNC);
            deleteJobRequest.setDelay(Duration.ofMillis(delay));
            deleteJobRequest.setDeleteMode(DeleteMode.EXPIRED);
            deleteJobRequest.setQuery(query);
            jobService.executeJob(deleteJobRequest);
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
            if (Strings.CS.startsWith(name, "ecm-sys:")) {
                log.warn(err);
                return null;
            }

            throw new BadDataException(err);
        }
        return ad;
    }

    private void fill(ActiveNode node, InputNodeRequest input, Set<String> contentPropertyNames, Set<OperationOption> optionSet) {
        ModelSchema schema = modelManager.getContextModel();
        if (sessionContext.getApiLevel() >= 2 && input.getTypeName() != null) {
            node.setTypeName(input.getTypeName());
        }

        Set<String> previousAspects = Set.copyOf(node.getData().getAspects());
        Set<String> aspects = node.getData().getAspects();
        var discardUnknownPreviousMetadata = optionSet.contains(DISCARD_UNKOWN_PRESENT_METADATA);
        if (!input.getAspects().isEmpty()) {
            var preservedAspects = aspects.stream()
                .filter(Objects::nonNull)
                .filter(name -> !input.getAspects().contains(name))
                .filter(name -> !sessionContext.getTenantData().map(TenantData::getImplicitAspects).orElse(Set.of()).contains(name))
                .filter(name -> managedAspects.contains(name) || name.startsWith("ecm-sys:") || name.startsWith("sys:"))
                .toList();

            aspects.clear();
            aspects.addAll(preservedAspects);
            aspects.addAll(input.getAspects()
                .stream()
                .filter(name -> !sessionContext.getTenantData().map(TenantData::getImplicitAspects).orElse(Set.of()).contains(name))
                .filter(name -> !Strings.CS.equals(name, ASPECT_ECMSYS_ASYNCREQUIRED))
                .map(name -> {
                    try {
                        return getAspect(schema, name);
                    } catch (BadDataException e) {
                        if (discardUnknownPreviousMetadata && previousAspects.contains(name)) {
                            log.warn("Discarding unknown previous aspect {} in the node {} (tenant {})", name, node.getId(), node.getTenant());
                            return null;
                        }

                        throw e;
                    }
                })
                .filter(Objects::nonNull)
                .map(TypedInterfaceDescriptor::getName)
                .toList());
        } else if (discardUnknownPreviousMetadata) {
            previousAspects.stream().filter(name -> {
                try {
                    getAspect(schema, name);
                    return false;
                } catch (BadDataException e) {
                    log.warn("Discarding unknown previous aspect {} in the node {} (tenant {})", name, node.getId(), node.getTenant());
                    return true;
                }
            }).forEach(aspects::remove);
        }

        input.getAspectOperations().forEach((a, op) -> {
            switch (op) {
                case ADD -> aspects.add(a);
                case REMOVE -> aspects.remove(a);
            }
        });

        if (input.getProperties().containsKey(CM_INITIAL_VERSION) || input.getProperties().containsKey(CM_AUTO_VERSION)) {
            aspects.add(ASPECT_CM_VERSIONABLE);
        }

        aspects.add(ASPECT_CM_AUDITABLE);
        aspects.add(ASPECT_SYS_REFERENCEABLE);
        aspects.remove(ASPECT_SYS_ARCHIVED);

        var allowManagedProperties = optionSet.contains(ALLOW_MANAGED_PROPERTIES);
        var ignoreManagedProperties = optionSet.contains(IGNORE_MANAGED_PROPERTIES);
        var handleContentProperties = optionSet.contains(HANDLE_CONTENT_PROPERTIES);
        var externalProperties = node.getExternalProperties();
        var properties = node.getData().getProperties();
        input.getProperties().entrySet()
            .stream()
            .filter(entry -> !generatedPropertySet.contains(entry.getKey()))
            .map(entry -> {
                try {
                    return propertyConverter.convertProperty(schema, entry.getKey(), entry.getValue());
                } catch (BadDataException e) {
                    if (discardUnknownPreviousMetadata && properties.containsKey(entry.getKey())) {
                        log.warn("Discarding unknown previous property {} in node {} (tenant {})", entry.getKey(), node.getId(), node.getTenant());
                        properties.remove(entry.getKey());
                        return null;
                    }

                    throw e;
                }
            })
            .filter(Objects::nonNull)
            .filter(pc -> handleContentProperties || !Strings.CS.equals(pc.getDescriptor().getType(), TYPE_CONTENT))
            .filter(pc -> nodeValidator.validateConstraints(pc))
            .forEach(pc -> {
                var value = propertyConverter.serializePropertyValue(pc.getDescriptor(), pc.getValue());
                var name = pc.getDescriptor().getName();

                if (Strings.CS.equals(pc.getDescriptor().getType(), TYPE_CONTENT)) {
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

                if (Strings.CS.equals(name, CM_OWNER)) {
                    permissionValidator.requireOwnership(node);
                }

                if (value == null) {
                    properties.remove(name);
                    if (pc.getDescriptor().isExternal()) {
                        externalProperties.put(name, null);
                    }
                } else if (value instanceof Optional<?> optional && optional.isEmpty()) {
                    if (pc.getDescriptor().isExternal()) {
                        properties.remove(name);
                        externalProperties.put(name, null);
                    } else {
                        properties.put(name, null);
                    }
                } else {
                    if (value instanceof Optional<?> optional) {
                        value = optional.get();
                    } else if (value instanceof PropertyValueOperation pvo) {
                        if (pc.getDescriptor().isMultiple()) {
                            if (!Strings.CS.equals(pc.getDescriptor().getType(), TYPE_CONTENT)) {
                                final var list = new ArrayList<>();
                                var previous = Optional.ofNullable(propertyConverter.convertProperty(pc.getDescriptor(), properties.get(name))).map(PropertyContainer::getValue).orElse(null);
                                if (previous instanceof Collection<?> c) {
                                    list.addAll(c);
                                } else if (previous != null) {
                                    list.add(previous);
                                }

                                mergeList(list, pvo);
                                value = list;
                            }
                        } else {
                            value = switch (pvo.getOp()) {
                                case ADD, APPEND, INSERT, PUSH, PUT -> pvo.getValue();
                                default -> throw new BadRequestException("Unsupported operation in single value property " + name + ": " + pvo.getOp());
                            };
                        }
                    }

                    if (pc.getDescriptor().isExternal()) {
                        properties.remove(name);
                        externalProperties.put(name, value);
                    } else {
                        properties.put(name, value);
                    }
                }
            });

        if (discardUnknownPreviousMetadata && !properties.isEmpty()) {
            var propertyNames = Set.copyOf(properties.keySet());
            propertyNames.stream()
                .filter(name -> schema.getProperty(name) == null)
                .peek(name -> log.warn("Discarding unknown previous property {} in node {} (tenant {})", name, node.getId(), node.getTenant()))
                .forEach(properties::remove);
        }

        // audit
        properties.put(CM_MODIFIER, sessionContext.getUserContext().getAuthorityRef().toString());
        properties.put(CM_MODIFIED, DateISO8601Utils.dateFormat.format(ZonedDateTime.now()));
        properties.remove(PROP_SYS_ARCHIVEDBY);
        properties.remove(PROP_SYS_ARCHIVEDDATE);
    }

    private void mergeList(final List<Object> list, PropertyValueOperation pvo) {
        switch (pvo.getOp()) {
            case POP:
                if (!list.isEmpty()) {
                    list.remove(0);
                }
                break;

            case REMOVE:
                if (pvo.getValue() instanceof Collection<?> c) {
                    list.removeAll(c);
                } else {
                    list.remove(pvo.getValue());
                }
                break;

            case PUT: {
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

            case APPEND, ADD:
                if (pvo.getValue() instanceof Collection<?> c) {
                    list.addAll(c);
                } else {
                    list.add(pvo.getValue());
                }
                break;

            case PUSH, INSERT:
                if (pvo.getValue() instanceof Collection<?> c) {
                    list.addAll(0, c);
                } else {
                    list.add(0, pvo.getValue());
                }
                break;

            case MULTI:
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
    public String copyNode(Vertex vertex, ParentLink link, boolean includeChildren, boolean includeAssociations, CopyMode copyMode) {
        return transactionManagerPort.perform(tx -> {
            AtomicLong counter = new AtomicLong(0);
            var copiedNode = copyVertex(tx, vertex, link, includeChildren, includeAssociations, copyMode, counter);
            sessionContext.getOperationCounter().addAndGet(counter.get());
            return PerformResult.<String>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(counter.get())
                .priorityUUIDs(Set.of(copiedNode.getUuid()))
                .result(copiedNode.getUuid())
                .build();
        });
    }

    private ActiveNode copyVertex(ApplicationTransaction tx, Vertex vertex, ParentLink link, boolean includeChildren, boolean includeAssociations, CopyMode copyMode, AtomicLong counter) {
        if (link == null) {
            throw new BadRequestException("Link is mandatory");
        }
        if (link.getParent() == null) {
            throw new BadRequestException("Parent vertex is mandatory");
        }
        if (link.getBindingType() != null && !link.getBindingType().equals(BindingType.HARD)) {
            throw new BadRequestException("BindingType must be 'hard'");
        }

        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                .scope(QueryScope.DEFAULT)
                .build();
        var r = nodeLoaderDAO.lookupNodes(List.of(vertex), qc);
        var node = Optional.ofNullable(r.getNode(vertex)).orElseThrow(() -> new NotFoundException("source: " + vertex.getValue()));
        var destination = nodeLoaderDAO.getNode(link.getParent(), qc).orElseThrow(() -> new NotFoundException("destination: " + link.getParent().getValue()));

        if (node.getId().equals(destination.getId())) {
            throw new BadRequestException("Cannot copy a node under itself");
        }

        permissionValidator.requirePermission(node, PermissionFlag.R);
        permissionValidator.requirePermission(node, PermissionFlag.C);

        linkManager.requireNoCycle(destination, node);

        node.getParents().stream().filter(Association::isHard).findFirst().ifPresent(a -> {
            if (link.getType() == null) {
                link.setType(a.getTypeName());
            }

            if (link.getName() == null) {
                link.setName(insertTimestampInNameAssociation(link.getParent(), a.getName(), null));
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
        if (link.getName() != null) {
            duplicatedNode.getProperties().put(CM_NAME, PrefixedQName.valueOf(link.getName()).getLocalPart());
        }
        duplicatedNode.getAspects().add(ASPECT_COPIED_NODE);
        duplicatedNode.setTx(tx);
        duplicatedNode.setTransactionFlags(IndexingFlags.formatAsBinary(IndexingFlags.FULL_FLAG_MASK));
        if (duplicatedNode.getData().getInternals() == null) {
            duplicatedNode.getData().setInternals(new HashMap<>());
        }
        duplicatedNode.getData().getInternals().put("ecm-sys:source-DBID", node.getId());
        nodeDAO.createNode(duplicatedNode);
        linkManager.createParentLink(tx, duplicatedNode, link, true, counter);

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
            // rebuild paths where tx excluding the newly created main duplicated node
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
        boolean nameFound = Optional.ofNullable(input.getLink()).map(ParentLink::getName).isPresent()
                || input.getAssociations().stream().map(EdgeItem::getName).anyMatch(Objects::nonNull);
        return !nameFound &&
            (input.getAspects().contains(ASPECT_ECMSYS_RENDITION) || (input.getAspectOperations().containsKey(ASPECT_ECMSYS_RENDITION) && Objects.equals(input.getAspectOperations().get(ASPECT_ECMSYS_RENDITION), ADD)))
            && input.getProperties().containsKey(PROP_ECMSYS_GENERATED)
            && Objects.equals(input.getProperties().get(PROP_ECMSYS_GENERATED), true);
    }
}
