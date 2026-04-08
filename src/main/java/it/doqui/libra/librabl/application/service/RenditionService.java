package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.model.rendition.*;
import it.doqui.libra.librabl.application.ports.out.XSLTFactoryPort;
import it.doqui.libra.librabl.application.ports.in.DocumentUseCase;
import it.doqui.libra.librabl.application.ports.in.RenditionUseCase;
import it.doqui.libra.librabl.application.ports.out.SearchPort;
import it.doqui.libra.librabl.application.ports.in.TemporaryUseCase;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.exceptions.DocumentOperationException;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.domain.policy.DeleteOptions;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeLoaderDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.QueryContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.FileData;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.graph.ContentRequest;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.application.model.properties.ExternalContentDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;
import static it.doqui.libra.librabl.domain.policy.MapOption.PARENT_HARD_ASSOCIATIONS;
import static it.doqui.libra.librabl.application.model.graph.InputNodeRequest.AspectOperation.ADD;
import static it.doqui.libra.librabl.application.model.graph.InputNodeRequest.AspectOperation.REMOVE;

@ApplicationScoped
@Slf4j
public class RenditionService implements RenditionUseCase {

    @Inject
    NodeManager nodeManager;

    @Inject
    ContentRepository contentStoreManager;

    @Inject
    AttachmentHelper contentRetriever;

    @Inject
    DocumentUseCase documentService;

    @Inject
    SearchPort searchPort;

    @Inject
    TemporaryUseCase temporaryUseCase;

    @Inject
    XSLTFactoryPort xsltFactory;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    LinkManager linkManager;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    private final String alg = "SHA-512";

    @Override
    public List<TransformerNode> findRenditionTransformers(ContentRequest cr) {
        var n = nodeLoaderDAO
            .getNode(new Vertex(VertexType.UUID, cr.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
            .map(node -> permissionValidator.requirePermission(node, PermissionFlag.R))
            .orElseThrow(() -> new NotFoundException("Node " + cr.getUuid() + " (xml Node) not found."));

        var xslIds = ObjectUtils.getAsStrings(n.getProperties().get(PROP_ECMSYS_XSLID));
        if (xslIds != null && !xslIds.isEmpty()) {
            var qc = QueryContext.builder()
                    .schema(sessionContext.getUserContext().getDbSchema())
                    .tenant(sessionContext.getTenant())
                    .build();
            var r = nodeLoaderDAO.lookupNodes(xslIds.stream().map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
            if (r.uuidMap().size() != xslIds.size()) {
                for (String xslId : xslIds) {
                    if (!r.uuidMap().containsKey(xslId)) {
                        throw new NotFoundException(xslId);
                    }
                }
            }

            return r.uuidMap()
                .values()
                .stream()
                .map(x -> asTransformer(x, null))
                .collect(Collectors.toList());
        } else {
            return List.of();
        }
    }

    @Override
    public Optional<TransformerNode> getRenditionTransformer(ContentRequest cr) {
        return nodeLoaderDAO
            .getNode(new Vertex(VertexType.UUID, cr.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
            .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
            .map(x -> asTransformer(x, cr.getContentPropertyName()));
    }

    @Override
    public List<RenditionNode> findRenditionNodes(ContentRequest xmlRef, ContentRequest transformerRef, Boolean generated, boolean oldModeEnabled) {
        var xmlNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, xmlRef.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(node -> permissionValidator.requirePermission(node, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException("Node " + xmlRef.getUuid() + " (xml Node) not found."));

        ActiveNode rtNode = null;
        if (transformerRef != null) {
            rtNode = nodeLoaderDAO
                    .getNode(new Vertex(VertexType.UUID, transformerRef.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                    .map(node -> permissionValidator.requirePermission(node, PermissionFlag.R))
                    .orElseThrow(() -> new NotFoundException("Node " + transformerRef.getUuid() + " (transformer Node) not found."));
        }

        var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
        var renditionUuids = new ArrayList<>(findRenditionUuidsFromRenditionMap(renditionMap, generated, rtNode != null ? rtNode.getUuid() : null));

        if (oldModeEnabled) {
            var renditionDocuments = new HashSet<String>();
            ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_XSLID)).forEach(s -> {
                if (transformerRef == null || transformerRef.getUuid().equals(s)) {
                    var node = nodeLoaderDAO
                            .getNode(new Vertex(VertexType.UUID, s), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                            .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                            .orElseThrow(() -> new NotFoundException("Node " + s + " (rt Node) not found."));
                    var renditionUuid = ObjectUtils.getAsString(node.getProperties().get(PROP_ECMSYS_RENDITIONID));
                    if (StringUtils.isNotBlank(renditionUuid)) {
                        renditionDocuments.add(renditionUuid);
                    }
                }
            });
            renditionUuids.addAll(renditionDocuments);
        }

        return mapUuidsToRenditions(renditionUuids);
    }

    @Override
    public TransformerNode createAndAssignTransformer(ContentRequest cr, TransformerIdentifiedInputRequest input) {
        return transactionManagerPort.perform(tx -> {
            try {
                var counter = new AtomicLong(0);
                var xmlNode = nodeLoaderDAO
                        .getNode(new Vertex(VertexType.UUID, cr.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                        .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                        .orElseThrow(() -> new NotFoundException(String.format("UUID %s (XML node) not found.", cr.getUuid())));

                if (hasNotContent(xmlNode)) {
                    throw new PreconditionFailedException(String.format("Node %s does not reference a content.", xmlNode.getUuid()));
                }

                String rtUuid;
                String hash;
                InputStream content = null;
                if (input.getRtUuid() != null) {
                    rtUuid = input.getRtUuid();
                    log.debug("Rendition transformer node to assign: {}", rtUuid);

                    updateTransformer(rtUuid, input);
                } else {
                    for (var value : input.getProperties().values()) {
                        if (value instanceof ExternalContentDescriptor ecd) {
                            content = Files.newInputStream(nodeManager.getNodeContent(ecd.getSource().getRef()).getFile().toPath());
                            break;
                        }
                    }

                    if (content == null) {
                        throw new RuntimeException("Content stream not found!");
                    }

                    hash = documentService.digest(content, alg).getDigest();

                    var nodeUuidWithSameHash = findFirstNodeOnSolr("@ecm\\-sys\\:hash: " + hash + " AND ASPECT:\"" + ASPECT_ECMSYS_TRANSFORMER + "\"");

                    if (nodeUuidWithSameHash != null) {
                        rtUuid = nodeUuidWithSameHash;
                        log.debug("Found node with same hash: {}. Assigning it to the xmlNode...", rtUuid);
                        updateTransformer(rtUuid, input);

                    } else {
                        log.debug("Node with same hash not found. Creating a new rtNode...");
                        input.getAssociations().stream().findFirst().ifPresent(p -> {
                            p.setPath(TRANSFORMER_PATH);
                            p.setCreateIfNotExists(true);
                        });
                        input.getProperties().put(PROP_ECMSYS_HASH, hash);
                        input.getAspectOperations().put(ASPECT_ECMSYS_TRANSFORMER, ADD);
                        input.getAspectOperations().put(ASPECT_ECMSYS_HASHABLE, ADD);
                        log.debug("Creating new node with {}", input);
                        rtUuid = nodeManager.createNode(input);
                    }
                }

                log.debug("Updating xmlNode adding related aspects and properties...");
                var xmlInputRequest = new InputNodeRequest();
                if (!xmlNode.getAspects().contains(ASPECT_ECMSYS_RENDITIONABLE)) {
                    xmlInputRequest.getAspectOperations().put(ASPECT_ECMSYS_RENDITIONABLE, ADD);
                }
                var xslIds = new HashSet<>(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_XSLID)));
                xslIds.add(rtUuid);
                xmlInputRequest.getProperties().put(PROP_ECMSYS_XSLID, xslIds);
                nodeManager.updateNode(xmlNode.getUuid(), xmlInputRequest, Set.of());
                counter.incrementAndGet();

                return PerformResult.<TransformerNode>builder()
                    .result(mapUuidAsTransformer(rtUuid))
                    .count(counter.incrementAndGet())
                    .mode(PerformResult.Mode.SYNC)
                    .priorityUUIDs(Set.of(rtUuid))
                    .build();
            } catch (IOException | SearchEngineException | NoSuchAlgorithmException e) {
                log.error(e.getMessage(), e);
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void deleteTransformer(ContentRequest xmlContentRequest, ContentRequest rtContentRequest) {
        transactionManagerPort.perform(tx -> {
            try {
                var counter = new AtomicLong(0);
                var xmlNode = nodeLoaderDAO
                    .getNode(new Vertex(VertexType.UUID, xmlContentRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                    .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                    .orElseThrow(() -> new NotFoundException(String.format("Node %s (XML Node) not found.", xmlContentRequest.getUuid())));

                var rtNode = nodeLoaderDAO.getNode(
                        new Vertex(VertexType.UUID, rtContentRequest.getUuid()),
                                QueryContext.builder()
                                        .schema(sessionContext.getUserContext().getDbSchema())
                                        .tenant(sessionContext.getTenant())
                                        .optionSet(Set.of(PARENT_HARD_ASSOCIATIONS))
                                        .build())
                    .map(n -> {
                        if (!permissionValidator.requirePermission(n, PermissionFlag.A, false)) {
                            n.getParents().forEach(a -> permissionValidator.requirePermission(a.getParent(), PermissionFlag.D));
                        }
                        return n;
                    })
                    .orElseThrow(() -> new NotFoundException(String.format("Node %s (transformer node) not found.", rtContentRequest.getUuid())));

                var xslIds = new HashSet<>(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_XSLID)));
                checkXMLTransformerRelation(xmlNode, rtNode);

                var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
                var renditionUuids = findRenditionUuidsFromRenditionMap(renditionMap, null, rtNode.getUuid());
                renditionUuids.forEach(renditionUuid -> linkManager.removeAllLinks(tx, renditionUuid, DeleteMode.DELETE, counter, Set.of(DeleteOptions.SKIP_RENDITION_CHECK)));

                if (findFirstNodeOnSolr("@ecm\\-sys\\:xslId: " + rtNode.getUuid() + " AND ASPECT:\"" + ASPECT_ECMSYS_RENDITIONABLE + "\" AND !ID:\"" + xmlNode.getUuid() + "\"") == null) {
                    linkManager.removeAllLinks(tx, rtNode.getUuid(), DeleteMode.DELETE, counter, Set.of(DeleteOptions.SKIP_RENDITION_CHECK));
                }

                log.debug("Updating related aspects and properties of xml node...");
                var xmlInputRequest = new InputNodeRequest();
                xslIds.remove(rtNode.getUuid());
                if (xslIds.isEmpty()) {
                    xmlInputRequest.getProperties().put(PROP_ECMSYS_XSLID, null);
                    xmlInputRequest.getAspectOperations().put(ASPECT_ECMSYS_RENDITIONABLE, REMOVE);
                } else {
                    xmlInputRequest.getProperties().put(PROP_ECMSYS_XSLID, xslIds);
                }

                renditionMap.remove(rtNode.getUuid());
                xmlInputRequest.getProperties().put(PROP_ECMSYS_RENDITIONMAP, RenditionMap.asList(renditionMap));
                nodeManager.updateNode(xmlNode.getUuid(), xmlInputRequest, Set.of());
                counter.incrementAndGet();

                return PerformResult.<Long>builder()
                    .result(tx.getId())
                    .priorityUUIDs(Set.of(rtNode.getUuid()))
                    .mode(PerformResult.Mode.SYNC)
                    .count(counter.get())
                    .build();
            } catch (IOException | SearchEngineException e) {
                log.error(e.getMessage(), e);
                throw new SystemException(e);
            }
        });
    }

    @Override
    public RenditionNode setNodeRendition(ContentRequest xml, ContentRequest rt, LinkedInputNodeRequest input) {
        return transactionManagerPort.perform(tx -> {
            var xmlNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, xml.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(String.format("Node %s (XML Node) not found.", xml.getUuid())));

            var rtNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, rt.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(String.format("Node %s (transformer node) not found.", rt.getUuid())));

            checkXMLTransformerRelation(xmlNode, rtNode);

            log.debug("Creating rendition node...");
            input.getAspectOperations().put(ASPECT_ECMSYS_RENDITION, ADD);
            input.getProperties().put(PROP_ECMSYS_GENERATED, false);
            input.getAssociations().stream().findFirst().ifPresent(a -> {
                a.setPath(RENDITION_PATH);
                a.setCreateIfNotExists(true);
            });
            var rdUuid = nodeManager.createNode(input);

            log.debug("Rendition created successfully. Updating renditionMap...");
            var xmlInputRequest = new InputNodeRequest();
            var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
            ObjectUtils.addValueInCollection(renditionMap, rtNode.getUuid(), rdUuid + "!");
            xmlInputRequest.getProperties().put(PROP_ECMSYS_RENDITIONMAP, RenditionMap.asList(renditionMap));
            nodeManager.updateNode(xmlNode.getUuid(), xmlInputRequest, Set.of());

            log.debug("RenditionMap updated successfully");
            return PerformResult.<RenditionNode>builder()
                .mode(PerformResult.Mode.SYNC)
                .tx(tx.getId())
                .count(2L)
                .result(mapUuidAsRendition(rdUuid))
                .priorityUUIDs(Set.of(rdUuid))
                .build();
        });
    }

    @Override
    public RenditionNode generateRendition(ContentRef renditionableRequest, ContentRef transformerRequest, RenditionSettings renditionSettings) {
        return transactionManagerPort.perform(tx -> {
            var counter = new AtomicLong(0);
            var rtNode = transactionManagerPort.doOnTenant(
                TenantRef.valueOf(StringUtils.isBlank(transformerRequest.getTenant()) ? sessionContext.getTenant() : transformerRequest.getTenant()),
                () -> {
                    var rt = nodeLoaderDAO
                        .getNode(new Vertex(VertexType.UUID, transformerRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                        .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                        .orElseThrow(() -> new NotFoundException(transformerRequest.getUuid()));

                    if (renditionSettings.isTransformerTempNode()) {
                        try {
                            log.debug("Received transformer from other tenant or \"raw\" content. Finding transformer node with same hash...");
                            var c = rt.getContent(ContentReferenceable.of(transformerRequest.getContentPropertyName())).orElseThrow(PreconditionFailedException::new);
                            var attachment = contentRetriever.attachment(rt, c);
                            var hash = documentService.digest(new FileInputStream(attachment.getFile()), alg).getDigest();
                            var nodeUuidWithSameHash = findFirstNodeOnSolr("@ecm\\-sys\\:hash: " + hash + " AND ASPECT:\"" + ASPECT_ECMSYS_TRANSFORMER + "\"");
                            if (nodeUuidWithSameHash == null) {
                                throw new PreconditionFailedException("Transformer node from content not found.");
                            }
                            log.debug("Found transformer with same hash: {}", nodeUuidWithSameHash);
                            rt = nodeLoaderDAO
                                .getNode(new Vertex(VertexType.UUID, nodeUuidWithSameHash), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                                .orElseThrow(() -> new NotFoundException(nodeUuidWithSameHash));
                        } catch (IOException | NoSuchAlgorithmException | SearchEngineException e) {
                            throw new SystemException(e);
                        }
                    }

                    if (hasNotRequiredAspect(rt, ASPECT_ECMSYS_TRANSFORMER)) {
                        throw new PreconditionFailedException(String.format("Node %s is not a transformer node.", rt.getUuid()));
                    }

                    return rt;
                }
            );

            var renditionableNode = transactionManagerPort.doOnTenant(
                TenantRef.valueOf(StringUtils.isBlank(renditionableRequest.getTenant()) ? sessionContext.getTenant() : renditionableRequest.getTenant()),
                () -> {
                    var renditionable = nodeLoaderDAO
                        .getNode(new Vertex(VertexType.UUID, renditionableRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                        .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                        .orElseThrow(() -> new NotFoundException(renditionableRequest.getUuid()));

                    if (renditionSettings.isRenditionableTempNode()) {
                        if (hasNotRequiredAspect(renditionable, ASPECT_ECMSYS_RENDITIONABLE)) {
                            throw new PreconditionFailedException(String.format("Node %s is not a renditionable node", renditionable.getUuid()));
                        }

                        var xslIds = ObjectUtils.getAsStrings(renditionable.getProperties().get(PROP_ECMSYS_XSLID));
                        if (!xslIds.contains(rtNode.getUuid())) {
                            throw new PreconditionFailedException(String.format("Transformer node %s has not been associated to renditionable node %s", rtNode.getUuid(), renditionable.getUuid()));
                        }
                    }

                    return renditionable;
                }
            );

            var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(renditionableNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
            var renditionUuids = new ArrayList<>(findRenditionUuidsFromRenditionMap(renditionMap, true, rtNode.getUuid()));

            RenditionNode result;
            if (renditionSettings.isForceGeneration() || renditionUuids.isEmpty()) {
                if (!renditionUuids.isEmpty()) {
                    ObjectUtils.removeValueFromCollection(renditionMap, rtNode.getUuid(), renditionUuids.get(0));
                    linkManager.removeAllLinks(tx, renditionUuids.get(0), DeleteMode.DELETE, counter, Set.of(DeleteOptions.SKIP_RENDITION_CHECK));
                }

                final NodeAttachment xmlAttachment;
                if (renditionSettings.getUnwrap() != null && renditionSettings.getUnwrap()) {
                    try {
                        var xmlRef = temporaryUseCase.createEphemeralNode(documentService.unwrap(renditionableRequest));
                        xmlAttachment = transactionManagerPort.doOnTemp(() -> {
                            var xmlNode = nodeLoaderDAO
                                .getNode(new Vertex(VertexType.UUID, xmlRef.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                                .orElseThrow(() -> new NotFoundException(xmlRef.getUuid()));
                            try {
                                var c = xmlNode.getContent(ContentReferenceable.of(CM_CONTENT)).orElseThrow(PreconditionFailedException::new);
                                return contentRetriever.attachment(xmlNode, c);
                            } catch (IOException e) {
                                log.debug("IOException: {}", e.getMessage());
                                throw new SystemException(e);
                            }
                        });
                    } catch (IOException | DocumentOperationException e) {
                        log.error("{}: {}", e.getMessage(), e.getCause().getMessage());
                        throw new SystemException(e);
                    }
                } else {
                    try {
                        var c = renditionableNode.getContent(ContentReferenceable.of(renditionableRequest.getContentPropertyName())).orElseThrow(PreconditionFailedException::new);
                        xmlAttachment = contentRetriever.attachment(renditionableNode, c);
                    } catch (IOException e) {
                        log.debug("IOException: {}", e.getMessage());
                        throw new SystemException(e);
                    }
                }

                try {
                    var c = rtNode.getContent(ContentReferenceable.of(transformerRequest.getContentPropertyName())).orElseThrow(PreconditionFailedException::new);
                    var rtAttachment = contentRetriever.attachment(rtNode, c);
                    try (var xmlFis = new FileInputStream(xmlAttachment.getFile()); var rtFis = new FileInputStream(rtAttachment.getFile())) {
                        var transformerBuffer = IOUtils.readFully(rtFis);
                        var transformer = xsltFactory.getXSLT(transformerBuffer);
                        var genMimeType = renditionSettings.getResultMimetype() == null ? ObjectUtils.getAsString(rtNode.getProperties().get(PROP_ECMSYS_GENMIMETYPE)) : null;

                        byte[] renditionContent = transformer.transform(new ByteArrayInputStream(transformerBuffer), xmlFis, genMimeType);

                        String rdCreated = null;
                        if (renditionableRequest.getIdentity() == null) {
                            var contentStream = new ContentStream();
                            contentStream.setInputStream(new ByteArrayInputStream(renditionContent));
                            if (renditionSettings.getNewRenditionInputRequest() != null) {
                                renditionSettings.getNewRenditionInputRequest().getAssociations().stream().findFirst().ifPresent(a -> {
                                    a.setPath(RENDITION_PATH);
                                    a.setCreateIfNotExists(true);
                                });
                                renditionSettings.getNewRenditionInputRequest().getProperties().put(PROP_ECMSYS_GENERATED, true);
                                renditionSettings.getNewRenditionInputRequest().getProperties().put(CM_CONTENT, contentStream);
                                renditionSettings.getNewRenditionInputRequest().getAspectOperations().put(ASPECT_ECMSYS_RENDITION, ADD);
                                //ecm-sys:renditionDescription is eventually provided
                                rdCreated = nodeManager.createNode(renditionSettings.getNewRenditionInputRequest());
                            } else {
                                var rdInput = new LinkedInputNodeRequest();
                                rdInput.getAspectOperations().put(ASPECT_ECMSYS_RENDITION, ADD);
                                rdInput.getProperties().put(CM_CONTENT, contentStream);
                                rdInput.getProperties().put(PROP_ECMSYS_GENERATED, true);
                                rdInput.getProperties().put(PROP_ECMSYS_RENDITION_DESCRIPTION, "Rendition node created automatically.");

                                var a = new LinkItemRequest();
                                a.setPath(RENDITION_PATH);
                                a.setCreateIfNotExists(true);
                                rdInput.getAssociations().add(a);
                                rdInput.setTypeName(CM_CONTENT);
                                rdCreated = nodeManager.createNode(rdInput);
                            }
                            counter.incrementAndGet();

                            log.debug("Updating renditionable node...");
                            ObjectUtils.addValueInCollection(renditionMap, rtNode.getUuid(), rdCreated);
                            var input = new InputNodeRequest();
                            input.getProperties().put(PROP_ECMSYS_RENDITIONMAP, RenditionMap.asList(renditionMap));
                            nodeManager.updateNode(renditionableNode.getUuid(), input, Set.of());
                            counter.incrementAndGet();
                        }

                        result = new RenditionNode();
                        result.setBinaryData(renditionContent);
                        result.setMimeType(StringUtils.isNotBlank(genMimeType) ? genMimeType : transformer.getDefaultMimeType());
                        result.setGenerated(true);
                        if (rdCreated != null) {
                            result.setUuid(rdCreated);
                        }
                    }
                } catch (IOException e) {
                    log.debug("IOException: {}", e.getMessage());
                    throw new SystemException(e);
                }
            } else {
                var rdNode = nodeLoaderDAO
                    .getNode(new Vertex(VertexType.UUID, renditionUuids.get(0)), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                    .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                    .orElseThrow(() -> new NotFoundException(renditionUuids.get(0)));

                result = asRendition(rdNode, null);
            }
            return PerformResult.<RenditionNode>builder()
                .result(result)
                .count(counter.get())
                .priorityUUIDs(counter.get() == 0 ? Set.of() : Set.of(result.getUuid(), renditionableNode.getUuid()))
                .tx(tx.getId())
                .mode(PerformResult.Mode.SYNC)
                .build();
        });
    }

    @Override
    public void deleteRenditions(ContentRequest xmlRequest, ContentRequest rtRequest, Boolean generated) {
        transactionManagerPort.perform(tx -> {
            var counter = new AtomicLong(0);
            var xmlNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, xmlRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(xmlRequest.getUuid()));

            var rtNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, rtRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(rtRequest.getUuid()));

            checkXMLTransformerRelation(xmlNode, rtNode);

            var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
            var toDelete = findRenditionUuidsFromRenditionMap(renditionMap, generated, rtNode.getUuid());

            toDelete.forEach(x -> {
                linkManager.removeAllLinks(tx, x, DeleteMode.DELETE, counter, Set.of(DeleteOptions.SKIP_RENDITION_CHECK));
                ObjectUtils.removeValueFromCollection(renditionMap, rtNode.getUuid(), x);
                ObjectUtils.removeValueFromCollection(renditionMap, rtNode.getUuid(), x + "!");
            });

            var input = new InputNodeRequest();
            input.getProperties().put(PROP_ECMSYS_RENDITIONMAP, RenditionMap.asList(renditionMap));
            nodeManager.updateNode(xmlNode.getUuid(), input, Set.of());
            counter.incrementAndGet();

            return PerformResult.<Long>builder()
                .result(tx.getId())
                .priorityUUIDs(Set.of(xmlNode.getUuid()))
                .mode(PerformResult.Mode.SYNC)
                .count(counter.get())
                .build();
        });
    }

    @Override
    public void deleteRendition(ContentRequest xmlRequest, ContentRequest rtRequest, ContentRequest rdRequest) {
        transactionManagerPort.perform(tx -> {
            var counter = new AtomicLong(0);
            var xmlNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, xmlRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(xmlRequest.getUuid()));

            var rtNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, rtRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(rtRequest.getUuid()));

            var rdNode = nodeLoaderDAO
                .getNode(new Vertex(VertexType.UUID, rdRequest.getUuid()), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                .orElseThrow(() -> new NotFoundException(rdRequest.getUuid()));

            checkXMLTransformerRelation(xmlNode, rtNode);

            var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
            var renditionMode = checkRenditionNode(renditionMap, rtNode, rdNode);
            log.debug("Deleting {} rendition {}...", renditionMode.name(), rdNode.getUuid());

            linkManager.removeAllLinks(tx, rdNode.getUuid(), DeleteMode.DELETE, counter, Set.of(DeleteOptions.SKIP_RENDITION_CHECK));

            ObjectUtils.removeValueFromCollection(
                renditionMap,
                rtNode.getUuid(),
                Objects.equals(renditionMode, RenditionMode.ASSIGNED) ? rdNode.getUuid() + "!" : rdNode.getUuid()
            );
            var input = new InputNodeRequest();
            input.getProperties().put(PROP_ECMSYS_RENDITIONMAP, RenditionMap.asList(renditionMap));
            nodeManager.updateNode(xmlNode.getUuid(), input, Set.of());
            counter.incrementAndGet();

            return PerformResult.<Long>builder()
                .result(tx.getId())
                .priorityUUIDs(Set.of(xmlNode.getUuid()))
                .mode(PerformResult.Mode.SYNC)
                .count(counter.get())
                .build();
        });
    }

    private boolean hasNotRequiredAspect(ActiveNode node, String aspect) {
        return hasNotContent(node) || !node.getAspects().contains(aspect);
    }

    private boolean hasNotContent(ActiveNode node) {
        return node.getData() == null || node.getData().getContents().stream().map(FileData::getFileURI).findFirst().isEmpty();
    }

    private Collection<String> findRenditionUuidsFromRenditionMap(Map<String, Collection<String>> renditionMap, Boolean generated, String transformerUuid) {
        Stream<String> uuidStream;
        if (transformerUuid != null) {
            if (renditionMap.get(transformerUuid) == null) {
                return Collections.emptyList();
            }
            uuidStream = renditionMap.get(transformerUuid).stream();
        } else {
            uuidStream = renditionMap.values().stream().flatMap(Collection::stream);
        }
        return uuidStream
            .filter(s -> {
                if (generated != null) {
                    if (generated) {
                        return !s.endsWith("!");
                    } else {
                        return s.endsWith("!");
                    }
                }
                return true;
            })
            .map(rd -> {
                if (rd.endsWith("!")) {
                    return rd.substring(0, rd.length() - 1);
                }
                return rd;
            })
            .collect(Collectors.toSet());
    }

    private List<RenditionNode> mapUuidsToRenditions(Collection<String> renditionUuids) {
        if (!renditionUuids.isEmpty()) {
            var qc = QueryContext.builder()
                    .schema(sessionContext.getUserContext().getDbSchema())
                    .tenant(sessionContext.getTenant())
                    .build();
            var r = nodeLoaderDAO.lookupNodes(renditionUuids.stream().map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
            if (r.uuidMap().size() != renditionUuids.size()) {
                for (String rdUuid : renditionUuids) {
                    if (!r.uuidMap().containsKey(rdUuid)) {
                        throw new NotFoundException("Rendition " + rdUuid + " not found.");
                    }
                }
            }

            return r.uuidMap().values().stream()
                .map(n -> asRendition(n, null))
                .collect(Collectors.toList());
        } else {
            return List.of();
        }
    }

    private void updateTransformer(String rtUuid, LinkedInputNodeRequest input) {
        var rtNode = nodeLoaderDAO
                .getNode(
                        new Vertex(VertexType.UUID, rtUuid),
                        QueryContext.builder()
                                .schema(sessionContext.getUserContext().getDbSchema())
                                .tenant(sessionContext.getTenant())
                                .build())
                .map(n -> permissionValidator.requirePermission(n, PermissionFlag.W))
                .orElseThrow(() -> new NotFoundException(rtUuid));

        if (!rtNode.getAspects().contains(ASPECT_ECMSYS_TRANSFORMER)) {
            input.getAspectOperations().put(ASPECT_ECMSYS_TRANSFORMER, ADD);
        }
        if (!rtNode.getAspects().contains(ASPECT_ECMSYS_HASHABLE)) {
            input.getAspectOperations().put(ASPECT_ECMSYS_HASHABLE, ADD);
        }
        nodeManager.updateNode(rtNode.getUuid(), input, Set.of(OperationOption.ALLOW_MANAGED_PROPERTIES));
    }

    private TransformerNode mapUuidAsTransformer(String nodeUuid) {
        var node = nodeLoaderDAO
            .getNode(new Vertex(VertexType.UUID, nodeUuid), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
            .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
            .orElseThrow(() -> new NotFoundException(nodeUuid));

        return asTransformer(node, null);
    }

    private TransformerNode asTransformer(ActiveNode node, String contentPropertyName) {
        var contentProperty = node.getData().getFileData(contentPropertyName);
        if (contentProperty == null) {
            throw new RuntimeException(String.format("Node %s has not any content property named %s", node.getUuid(), contentPropertyName));
        }

        final Map<String, Object> properties = node.getData().getProperties();

        TransformerNode result = new TransformerNode();
        result.setUuid(node.getUuid());
        result.setDescription(ObjectUtils.getAsString(properties.get(PROP_ECMSYS_TRANSFORMER_DESCRIPTION)));

        try {
            String contentUrl = contentProperty.getContentUrl();
            result.setBinaryData(IOUtils.readFully(Files.newInputStream(contentStoreManager.getPath(contentUrl))));
        } catch (IOException e) {
            throw new DataIOException(e.getMessage());
        }

        String genMimeType = ObjectUtils.getAsString(properties.get(PROP_ECMSYS_GENMIMETYPE));
        if (StringUtils.isBlank(genMimeType)) {
            var t = xsltFactory.getXSLT(result.getBinaryData());
            genMimeType = t.getDefaultMimeType();
        }
        result.setGenMimeType(genMimeType);
//        result.setRenditionId(ObjectUtils.getAsString(properties.get(PROP_ECMSYS_RENDITIONID)));
        result.setMimeType(contentProperty.getMimetype());
        result.setHash(ObjectUtils.getAsString(properties.get(PROP_ECMSYS_HASH)));

        return result;
    }

    private RenditionNode mapUuidAsRendition(String nodeUuid) {
        var node = nodeLoaderDAO
            .getNode(new Vertex(VertexType.UUID, nodeUuid), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
            .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
            .orElseThrow(() -> new NotFoundException(nodeUuid));

        return asRendition(node, null);
    }

    private RenditionNode asRendition(ActiveNode node, String contentPropertyName) {
        var contentProperty = node.getData().getFileData(contentPropertyName);
        var r = new RenditionNode();
        r.setUuid(node.getUuid());
        r.setDescription(ObjectUtils.getAsString(node.getProperties().get(PROP_ECMSYS_RENDITION_DESCRIPTION)));
        r.setMimeType(contentProperty.getMimetype());
        r.setGenerated(ObjectUtils.getAsBoolean(node.getProperties().get(PROP_ECMSYS_GENERATED), false));

        try {
            var c = node.getContent(ContentReferenceable.of(contentPropertyName)).orElseThrow(PreconditionFailedException::new);
            r.setBinaryData(IOUtils.readFully(Files.newInputStream(contentRetriever.attachment(node, c).getFile().toPath())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    private void checkXMLTransformerRelation(ActiveNode xmlNode, ActiveNode rtNode) {
        if (hasNotRequiredAspect(xmlNode, ASPECT_ECMSYS_RENDITIONABLE)) {
            throw new PreconditionFailedException(String.format("Node %s is not a renditionable node", xmlNode.getUuid()));
        }
        if (hasNotRequiredAspect(rtNode, ASPECT_ECMSYS_TRANSFORMER)) {
            throw new PreconditionFailedException(String.format("Node %s is not a transformer node", rtNode.getUuid()));
        }
        if (!ObjectUtils.getAsStrings(xmlNode.getProperties().get(PROP_ECMSYS_XSLID)).contains(rtNode.getUuid())) {
            throw new PreconditionFailedException("RtNode " + rtNode.getUuid() + " has not been assigned to xmlNode " + xmlNode.getUuid() + " as its transformer");
        }
    }

    private String findFirstNodeOnSolr(String luceneQuery) throws SearchEngineException, IOException {
        var pageable = new Pageable();
        pageable.setSize(1);
        pageable.setPage(0);
        Paged<String> uuidsWithSameHash = searchPort.findNodes(luceneQuery, List.of(), pageable);
        if (uuidsWithSameHash.getTotalElements() == 1) {
            return uuidsWithSameHash.getItems().get(0);
        } else {
            return null;
        }
    }

    private RenditionMode checkRenditionNode(Map<String, Collection<String>> renditionMap, ActiveNode rtNode, ActiveNode rdNode) {
        if (hasNotRequiredAspect(rdNode, ASPECT_ECMSYS_RENDITION)) {
            throw new PreconditionFailedException(String.format("Node %s is not a rendition node", rdNode.getUuid()));
        }

        var renditions = renditionMap.get(rtNode.getUuid());
        if (renditions == null || renditions.isEmpty() || (!renditions.contains(rdNode.getUuid()) && !renditions.contains(rdNode.getUuid() + "!"))) {
            throw new NotFoundException(String.format("Relation between %s (transformer node) and %s (rendition node) not found", rtNode.getUuid(), rdNode.getUuid()));
        }

        if (renditions.contains(rdNode.getUuid() + "!")) {
            return RenditionMode.ASSIGNED;
        } else {
            return RenditionMode.GENERATED;
        }
    }

    public void renditionableUpdates(ActiveNode renditionNode, AtomicLong counter) {
        if (renditionNode.getAspects().contains(ASPECT_ECMSYS_TRANSFORMER)) {
            throw new ForbiddenException(renditionNode.getUuid());
        }
        try {
            if (renditionNode.getAspects().contains(ASPECT_ECMSYS_RENDITION)) {
                var renditionableUuids = searchPort.findNodes(
                    "ASPECT: \"" + ASPECT_ECMSYS_RENDITIONABLE + "\" AND @ecm\\-sys\\:renditionMap: \"*=" + renditionNode.getUuid() + "*\"",
                    List.of(),
                    null
                );

                for (String renditionableUuid : renditionableUuids.getItems()) {
                    var renditionableNode = nodeLoaderDAO
                        .getNode(new Vertex(VertexType.UUID, renditionableUuid), QueryContext.builder().schema(sessionContext.getUserContext().getDbSchema()).tenant(sessionContext.getTenant()).build())
                        .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                        .orElseThrow(() -> new NotFoundException(renditionNode.getUuid() + " (renditionable node) not found."));

                    var renditionMap = RenditionMap.parse(ObjectUtils.getAsStrings(renditionableNode.getProperties().get(PROP_ECMSYS_RENDITIONMAP)));
                    for (var entry : renditionMap.entrySet()) {
                        if (entry.getValue().contains(renditionNode.getUuid())) {
                            ObjectUtils.removeValueFromCollection(renditionMap, entry.getKey(), renditionNode.getUuid());
                        }
                        if (entry.getValue().contains(renditionNode.getUuid() + "!")) {
                            ObjectUtils.removeValueFromCollection(renditionMap, entry.getKey(), renditionNode.getUuid() + "!");
                        }
                    }

                    var input = new InputNodeRequest();
                    input.getProperties().put(PROP_ECMSYS_RENDITIONMAP, RenditionMap.asList(renditionMap));
                    nodeManager.updateNode(renditionableNode.getUuid(), input, Set.of());
                    counter.incrementAndGet();
                }
            }
        } catch (SearchEngineException | IOException e) {
            log.debug("{}: {}", e.getMessage(), e.getCause().getMessage());
            throw new SystemException(e);
        }
    }

//    todo: private boolean autoUnwrap(String mimetype) {
//        return StringUtils.equalsIgnoreCase(mimetype, "p7m", "p7s", "p7b")
//    }
}
