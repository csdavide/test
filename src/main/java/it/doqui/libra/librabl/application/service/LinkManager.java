package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.application.mappers.PropertyConverter;
import it.doqui.libra.librabl.application.support.PathUtils;
import it.doqui.libra.librabl.domain.model.graph.Constants;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.ports.out.SearchPort;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.domain.model.graph.BindingType;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.schema.PropertyDescriptor;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantLimit;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.domain.policy.DeleteOptions;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.ports.out.ConfigurationRepository;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.Association;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.association.LinkItem;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;
import static it.doqui.libra.librabl.domain.model.graph.IndexingFlags.PATH_FLAG;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.TYPE_DATETIME;

@ApplicationScoped
@Slf4j
public class LinkManager {

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    PathDAO pathDAO;

    @Inject
    AssociationDAO associationDAO;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    NodeValidator nodeValidator;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    PropertyConverter propertyConverter;

    @Inject
    NodeUseCase nodeService;

    @Inject
    RenditionService renditionService;

    @Inject
    SearchPort searchPort;

    @Inject
    ConfigurationRepository configurationRepository;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    public void renameLink(ApplicationTransaction tx, Vertex parent, Vertex child, String name, AtomicLong counter) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                .scope(QueryScope.DEFAULT)
                .build();
        var r = nodeLoaderDAO.lookupNodes(List.of(child, parent), qc);
        var p = r.getNode(parent);
        var c = r.getNode(child);
        renameLink(tx, p, c, name, counter);
    }

    void renameLink(ApplicationTransaction tx, String parentUUID, String childUUID, String name, AtomicLong counter) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS))
                .scope(QueryScope.DEFAULT)
                .build();
        var r = nodeLoaderDAO.lookupNodes(List.of(new Vertex(VertexType.UUID, parentUUID), new Vertex(VertexType.UUID, childUUID)), qc);
        var parent = Optional.ofNullable(r.getNode(new Vertex(VertexType.UUID, parentUUID))).orElseThrow(() -> new NotFoundException(parentUUID));
        var child = Optional.ofNullable(r.getNode(new Vertex(VertexType.UUID, childUUID))).orElseThrow(() -> new NotFoundException(childUUID));
        renameLink(tx, parent, child, name, counter);
    }

    public void renameLinks(ApplicationTransaction tx, ActiveNode node, LinkMode mode, String name, AtomicLong counter) {
        var associations = node.getParents().stream()
            .filter(a -> mode == LinkMode.ALL || a.isHard())
            .toList();

        if (associations.isEmpty()) {
            throw new SystemException("No hard association found for uuid " + node.getUuid());
        }

        if (mode == LinkMode.FIRST) {
            associations = associations.stream()
                .findFirst().map(List::of)
                .orElseThrow(() -> new SystemException("No hard association found for uuid " + node.getUuid()));
        }

        associations = associations.stream()
            .filter(a -> !Strings.CS.equals(a.getName(), name))
            .toList();

        if (!associations.isEmpty()) {
            var qc = QueryContext.builder()
                    .schema(sessionContext.getUserContext().getDbSchema())
                    .tenant(sessionContext.getTenant())
                    .optionSet(Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS))
                    .scope(QueryScope.DEFAULT)
                    .build();
            var r = nodeLoaderDAO.lookupNodes(associations.stream().map(a -> a.getParent().getUuid()).map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
            for (var association : associations) {
                renameLink(tx, r.uuidMap().get(association.getParent().getUuid()), node, name, counter);
            }
        }
    }

    private Association renameLink(ApplicationTransaction tx, ActiveNode parent, ActiveNode child, String name, AtomicLong counter) {
        var association = child.getParents().stream()
            .filter(a -> Objects.equals(a.getParent().getId(), parent.getId()))
            .findAny()
            .orElseThrow(() -> new NotFoundException(String.format("%s->%s", parent.getUuid(), child.getUuid())));

        var affectedNodes = computeDescendants(child, parent);
        var limit = configurationRepository.getLimit(TenantLimit.Operation.RENAME, sessionContext.getMode(), null);
        if (affectedNodes > limit) {
            log.error("Too many descendants touched, operation cancelled");
            throw new LimitExceededException("Limit exceeded, operation cancelled");
        }

        permissionValidator.requirePermission(parent, PermissionFlag.C);
        permissionValidator.requirePermission(child, PermissionFlag.W);
        associationDAO.renameAssociation(association, tx.getId(), name);

        counter.addAndGet(affectedNodes);
        return association;
    }

    Association moveParentLink(ApplicationTransaction tx, Vertex vertex, ParentLink link, AtomicLong counter) {
        if (link == null) {
            throw new BadRequestException("Link is mandatory");
        }

        if (link.getParent() == null) {
            throw new BadRequestException("Parent vertex is mandatory");
        }

        if (link.getBindingType() != null && link.getBindingType() != BindingType.HARD) {
            throw new BadRequestException("Binding type must be 'hard'");
        }

        var r = nodeLoaderDAO.lookupNodes(List.of(vertex), QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                .scope(QueryScope.DEFAULT)
                .build());
        var child = Optional.ofNullable(r.getNode(vertex)).orElseThrow(() -> new NotFoundException(vertex.getValue()));
        var parent = nodeLoaderDAO.getNode(link.getParent(), QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG))
                .scope(QueryScope.DEFAULT)
                .build())
                .orElseThrow(() -> new NotFoundException(link.getParent().getValue()));

        var sourceAssociations = child.getParents().stream().filter(Association::isHard).toList();
        var matchingAssociation = sourceAssociations.stream().filter(a -> Objects.equals(a.getParent().getId(), parent.getId())).findFirst();
        if (matchingAssociation.isPresent()) {
            var oldName = matchingAssociation.map(Association::getName).orElse(null);
            var oldType = matchingAssociation.map(Association::getTypeName).orElse(null);
            if (link.getType() != null && !link.getType().equals(oldType)) {
                throw new ForbiddenException("Cannot change type of association!");
            }
            if (link.getName() != null && link.getName().equals(oldName)) {
                return matchingAssociation.get();
            } else {
                return renameLink(tx, parent, child, link.getName(), counter);
            }
        }

        var primaryAssociation = sourceAssociations.stream().findFirst().orElseThrow(() -> new SystemException("Unable to find primary association for " + child.getUuid()));
        // set values for missing fields
        link.setBindingType(BindingType.HARD);
        if (link.getType() == null) {
            link.setType(primaryAssociation.getTypeName());
        }
        if (link.getName() == null) {
            link.setName(primaryAssociation.getName());
        }

        var createdAssociation = createParentLink(tx, child, link, false, counter);
        if (createdAssociation != null) {
            for (Association a : sourceAssociations) {
                removeAssociation(tx, a.getParent(), child, a.getTypeName(), a.getName(), null, DeleteMode.DELETE, Set.of());
            }
        }

        return createdAssociation;
    }

    Association createParentLink(ApplicationTransaction tx, Vertex vertex, ParentLink link, boolean createMissingPath, AtomicLong counter) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                .scope(QueryScope.DEFAULT)
                .build();
        var child = nodeLoaderDAO
                .getNode(vertex, qc)
                .orElseThrow(() -> new NotFoundException(vertex.toString()));
        permissionValidator.requirePermission(child, PermissionFlag.R);
        return createParentLink(tx, child, link, createMissingPath, counter);
    }

    Association createParentLink(ApplicationTransaction tx, ActiveNode child, ParentLink link, boolean createMissingPath, AtomicLong counter) {
        if (link == null) {
            throw new BadRequestException("Link is mandatory");
        }

        if (link.getBindingType() == null) {
            throw new BadRequestException("Binding type is mandatory");
        }

        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG))
                .scope(QueryScope.DEFAULT)
                .build();
        var parent = nodeLoaderDAO.getNode(link.getParent(), qc).orElse(null);
        return createParentLink(tx, child, parent, link, createMissingPath, counter);
    }

    private Association createParentLink(ApplicationTransaction tx, ActiveNode child, ActiveNode parent, ParentLink link, boolean createMissingPath, AtomicLong counter) {
        if (parent == null) {
            if (createMissingPath && link.getParent().getType() == VertexType.PATH && link.getBindingType() == BindingType.HARD) {
                var parentUUID = createPath(link.getParent().getValue(), new HashMap<>());
                if (parentUUID == null) {
                    throw new BadRequestException("Unable to create missing path " + link.getParent().getValue());
                }

                var qc = QueryContext.builder()
                        .schema(sessionContext.getUserContext().getDbSchema())
                        .tenant(sessionContext.getTenant())
                        .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG))
                        .scope(QueryScope.DEFAULT)
                        .build();
                parent = nodeLoaderDAO.getNode(new Vertex(VertexType.UUID, parentUUID), qc)
                        .orElseThrow(() -> new NotFoundException(parentUUID));
            } else {
                throw new NotFoundException(link.getParent().toString());
            }
        } else {
            permissionValidator.requirePermission(parent, PermissionFlag.C);
        }

        long affectedNodes = 1;
        if (link.getBindingType() != BindingType.LOOSE) {
            requireNoCycle(parent, child);
            affectedNodes = computeDescendants(child, null);
            var limit = configurationRepository.getLimit(TenantLimit.Operation.LINK, sessionContext.getMode(), null);
            if (affectedNodes > limit) {
                log.error("Too many descendants touched, operation aborted");
                throw new LimitExceededException("Limit exceeded, operation aborted");
            }
        }

        var association = createAssociation(tx, parent, child,
            Optional.ofNullable(link.getType()).orElse(CM_CONTAINS),
            Optional.ofNullable(link.getName()).orElse(child.getProperties().get(CM_NAME).toString()),
            link.getHard());
        nodeDAO.setTransaction(association.getChild().getId(), tx.getId(), PATH_FLAG);
        if (counter != null) {
            counter.addAndGet(affectedNodes);
        }

        return association;
    }

    Association removeParentLink(ApplicationTransaction tx, Vertex vertex, ParentLink link, AtomicLong counter) {
        if (link == null) {
            throw new BadRequestException("Link is mandatory");
        }

        if (link.getParent() == null) {
            throw new BadRequestException("Parent vertex is mandatory");
        }

        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                .scope(QueryScope.DEFAULT)
                .build();
        var r = nodeLoaderDAO.lookupNodes(List.of(vertex, link.getParent()), qc);
        var child = Optional.ofNullable(r.getNode(vertex)).orElseThrow(() -> new NotFoundException(vertex.getValue()));
        var parent = Optional.ofNullable(r.getNode(link.getParent())).orElseThrow(() -> new NotFoundException(link.getParent().toString()));
        return removeAssociation(tx, parent, child, link.getType(), link.getName(), counter, DeleteMode.DELETE, Set.of());
    }

    void removeAllLinks(ApplicationTransaction tx, String uuid, DeleteMode deleteMode, AtomicLong counter, Set<DeleteOptions> deleteOptions) {
        var node = nodeLoaderDAO.getNode(
                new Vertex(VertexType.UUID, uuid),
                QueryContext.builder()
                        .schema(sessionContext.getUserContext().getDbSchema())
                        .tenant(sessionContext.getTenant())
                        .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                        .build());

        if (node.isEmpty()) {
            if (deleteMode != DeleteMode.EXPIRED) {
                throw new NotFoundException(uuid);
            }

            log.debug("Node {} not found on tenant {} for deletion if expired", uuid, sessionContext.getTenant());
        } else {
            removeAllLinks(tx, node.get(), deleteMode, counter, deleteOptions);
        }
    }

    private void removeAllLinks(ApplicationTransaction tx, ActiveNode node, DeleteMode deleteMode, AtomicLong counter, Set<DeleteOptions> deleteOptions) {
        if (deleteMode == DeleteMode.EXPIRED) {
            var pd = new PropertyDescriptor();
            pd.setName(Constants.PROP_ECMSYS_EXPIRES_AT);
            pd.setType(TYPE_DATETIME);
            if (propertyConverter.convertPropertyValue(pd, node.getProperties().get(Constants.PROP_ECMSYS_EXPIRES_AT)) instanceof ZonedDateTime expiresAt) {
                if (expiresAt.isAfter(ZonedDateTime.now())) {
                    log.debug("Node {} on tenant {} not yet expired: it will expire at {}", node.getUuid(), sessionContext.getTenant(), expiresAt);
                    return;
                }
            } else {
                log.debug("Node {} on tenant {} has no expiration datetime", node.getUuid(), sessionContext.getTenant());
                return;
            }
        }

        var links = node.getParents().stream().map(a  -> nodeMapper.mapParent(a)).map(LinkItemRequest::new).toList();
        var parentMap = retrieveRelatedNodes(null, links, Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS));

        var associations = new ArrayList<>(node.getParents().stream().filter(Association::isHard).toList());
        if (associations.isEmpty()) {
            log.debug("Deleting node {} without parents", node.getUuid());
            var a = new Association();
            a.setHard(true);
            a.setChild(node);
            associationDAO.deleteAssociation(a, tx.getId(), true, deleteMode, counter, false);
        } else {
            for (var i = associations.size() - 1; i >= 0; i--) {
                var item = associations.get(i);
                var parent = parentMap.get(item.getParent().getUuid());
                removeAssociation(tx, parent, node, item.getTypeName(), item.getName(), counter, deleteMode, deleteOptions);
            }
        }
    }

    private Association removeAssociation(ApplicationTransaction tx, ActiveNode parent, ActiveNode child, String associationType, String associationName, AtomicLong counter, DeleteMode deleteMode, Set<DeleteOptions> deleteOptions) {
        if (!permissionValidator.requirePermission(child, PermissionFlag.A, false)) {
            permissionValidator.requirePermission(parent, PermissionFlag.D);
        }
        var association = child.getParents().stream()
            .filter(a -> Objects.equals(a.getParent().getId(), parent.getId()))
            .findFirst()
            .orElseThrow(() -> new PreconditionFailedException(String.format("Unable to find association between %s and %s", parent.getUuid(), child.getUuid())));

        if (associationType != null && !Strings.CS.equals(association.getTypeName(), associationType)) {
            throw new PreconditionFailedException("Association has a different type than requested one: " + associationType);
        }

        if (associationName != null && !Strings.CS.equals(association.getName(), associationName)) {
            throw new PreconditionFailedException("Association has a different name than requested one: " + associationName);
        }

        var archive = association.isHard() && child.getParents().stream().noneMatch(a -> a.isHard() && !Objects.equals(a.getId(), association.getId()));
        if (archive && !deleteOptions.contains(DeleteOptions.SKIP_RENDITION_CHECK)) {
            renditionService.renditionableUpdates(child, counter);
        }

        if (archive && child.getAspects().contains(ASPECT_UNREMOVABLE)) {
            throw new SystemException("Unremovable node " + child.getUuid());
        }

        var enableInsertMode = false;
        var affectedNodes = computeDescendants(child, parent);
        var limit = configurationRepository.getLimit(TenantLimit.Operation.DELETE, sessionContext.getMode(), TenantLimit.LimitFeature.DEFAULT);
        if (affectedNodes > limit) {
            log.debug("High number of descendants will be touched: insert mode will be applied");
            enableInsertMode = true;
        }

        if (!associationDAO.deleteAssociation(association, tx.getId(), archive, deleteMode, counter, enableInsertMode)) {
            throw new RuntimeException("Unable to delete association " + association.getId());
        }

        return association;
    }

    List<Association> createAssociations(ApplicationTransaction tx, ActiveNode node, Map<String,ActiveNode> nodeMap, Collection<? extends LinkItem> links, AtomicLong counter) {
        validateAssociations(node, nodeMap, links);
        var associations = new ArrayList<Association>();
        var parentSet = node.getParents().stream().map(p -> p.getParent().getUuid()).collect(Collectors.toSet());
        for (LinkItem link : links) {
            log.trace("Creating association for node {} link {}", node.getUuid(), link);
            final ActiveNode vertex = nodeMap.get(link.getVertexUUID());
            final ActiveNode parent;
            final ActiveNode child;
            final Boolean hard;

            switch (link.getRelationship()) {
                case PARENT:
                    if (parentSet.contains(vertex.getUuid())) {
                        log.warn("Association between node {} and parent {} already exists", node.getUuid(), link.getVertexUUID());
                        continue;
                    }

                    parent = vertex;
                    child = node;
                    hard = link.isHard();
                    break;

                case SOURCE:
                    parent = vertex;
                    child = node;
                    hard = null;
                    break;

                case CHILD:
                    parent = node;
                    child = vertex;
                    hard = link.isHard();
                    break;

                case TARGET:
                    parent = node;
                    child = vertex;
                    hard = null;
                    break;

                default:
                    throw new RuntimeException("Unexpected relationship: " + link.getRelationship());
            }

            if (hard != null) {
                requireNoCycle(parent, child);
            }

            permissionValidator.requirePermission(parent, PermissionFlag.C);

            long affectedNodes = 1;
            if (hard != null) {
                affectedNodes = computeDescendants(child, null);
                var limit = configurationRepository.getLimit(TenantLimit.Operation.LINK, sessionContext.getMode(), null);
                if (affectedNodes > limit) {
                    log.error("Too many descendants touched, operation cancelled");
                    throw new LimitExceededException("Limit exceeded, operation cancelled");
                }
            } else if (counter != null) {
                counter.incrementAndGet();
            }

            var association = createAssociation(tx, parent, child, link.getTypeName(), link.getName(), hard);
            associations.add(association);
            if (counter != null) {
                counter.addAndGet(affectedNodes);
            }
        }

        return associations;
    }

    private Association createAssociation(ApplicationTransaction tx, ActiveNode parent, ActiveNode child, String type, String name, Boolean hard) {
        boolean duplicatesAllowed = sessionContext.getTenantData().map(TenantData::isDuplicatesAllowed).orElse(false);
        Association association = new Association();
        association.setParent(parent);
        association.setChild(child);
        association.setTypeName(type);
        association.setName(name);
        association.setHard(hard);
        association.setCode(name == null || duplicatesAllowed ? null : name.toLowerCase());

        associationDAO.createAssociation(tx, association);
        return association;
    }

    void requireNoCycle(ActiveNode parent, ActiveNode child) {
        Set<Long> ancestors = parent.getPaths()
            .stream()
            .flatMap(p -> Arrays.stream(p.getPath().split(":")))
            .filter(StringUtils::isNotBlank)
            .map(Long::valueOf)
            .collect(Collectors.toSet());

        if (ancestors.contains(child.getId())) {
            throw new BadRequestException(String.format("Cannot create a circular relationship: %s is an ancestor of %s", child.getUuid(), parent.getUuid()));
        }
    }

    Map<String,ActiveNode> retrieveRelatedNodes(String uuid, Collection<LinkItemRequest> associations, Set<MapOption> options) {
        final var pathMap = new HashMap<String,LinkItemRequest>();
        final var linkMap = new LinkedHashMap<String, LinkItem>();
        try {
            LinkItem primaryParent = null;
            for (var association : associations) {
                if (association.getVertexUUID() == null) {
                    Objects.requireNonNull(association.getPath(), "Either UUID or path must be specified in the association");
                    if (!association.getPath().endsWith("/")) {
                        association.setPath(association.getPath() + "/");
                    }

                    pathMap.put(association.getPath(), association);
                } else {
                    ObjectUtils.requireNull(association.getPath(), "Both UUID and path in the association cannot be specified");
                    linkMap.put(association.getVertexUUID(), association);
                }

                if (primaryParent == null && association.isHard() && association.getRelationship() == RelationshipKind.PARENT) {
                    primaryParent = association;
                } else if (association.getRelationship() == RelationshipKind.CHILD) {
                    transactionManagerPort.options().disableWithInTxMode();
                }
            } // end for
        } catch (NullPointerException e) {
            throw new BadRequestException(e.getMessage());
        }

        if (!pathMap.isEmpty()) {
            var map = nodeDAO.mapUUIDInFilePaths(pathMap.keySet());
            for (var entry : pathMap.entrySet()) {
                var vertexUUID = map.get(entry.getKey());
                if (vertexUUID == null) {
                    if (entry.getValue().isCreateIfNotExists()) {
                        vertexUUID = createPath(entry.getKey(), map);
                        if (vertexUUID == null) {
                            throw new BadRequestException("Unable to create missing path " + entry.getKey());
                        }
                    } else {
                        throw new BadRequestException("Unable to find path " + entry.getKey());
                    }
                }

                entry.getValue().setVertexUUID(vertexUUID);
                linkMap.put(vertexUUID, entry.getValue());
            }
        }

        var uuids = new LinkedList<>(linkMap.keySet());
        if (uuid != null) {
            uuids.add(uuid);
        }

        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(options)
                .scope(QueryScope.DEFAULT)
                .build();
        var r = nodeLoaderDAO.lookupNodes(uuids.stream().map(u -> new Vertex(VertexType.UUID, u)).toList(), qc);
        // check if all nodes have been found
        if (uuid != null && !r.uuidMap().containsKey(uuid)) {
            throw new NotFoundException(uuid);
        } else {
            final List<String> unknownUUIDs = linkMap.keySet().stream().filter(id -> !r.uuidMap().containsKey(id)).toList();
            if (!unknownUUIDs.isEmpty()) {
                throw new NotFoundException(String.join(",", unknownUUIDs));
            }
        }

        return r.uuidMap();
    }

    private String createPath(String path, Map<String, String> map) {
        log.debug("Creating path {}", path);
        String[] elements = PathUtils.normalizePath(path).split("/");
        var partialPath = new StringBuilder();
        var partialPaths = new ArrayList<String>();
        for (int n = 0; n < elements.length; n++) {
            partialPath.append(elements[n]).append("/");
            if (n > 0) {
                partialPaths.add(partialPath.toString());
            }
        }

        var p = pathDAO.findLongestPathNode(partialPaths);
        String uuid = null;
        var match = false;
        if (p.isPresent()) {
            var longestPath = p.get().getLeft();
            uuid = p.get().getRight();
            log.debug("Found partial path {} having {}", longestPath, uuid);
            for (var element : partialPaths) {
                if (match) {
                    var s = element.split("/");
                    uuid = nodeService.createNode(createFolder(uuid, s[s.length - 1]));
                    log.debug("Created node {} having path {}", uuid, element);
                    if (map != null) {
                        map.put(element, uuid);
                    }
                } else if (Strings.CS.equals(element, longestPath)) {
                    match = true;
                }
            }
        }

        return match ? uuid : null;
    }

    private LinkedInputNodeRequest createFolder(String parentUUID, String name) {
        var folder = new LinkedInputNodeRequest();
        folder.setTypeName(Constants.CM_FOLDER);
        folder.getProperties().put(Constants.CM_NAME, PrefixedQName.valueOf(name).getLocalPart());

        var link = new LinkItemRequest();
        link.setRelationship(RelationshipKind.PARENT);
        link.setVertexUUID(parentUUID);
        link.setTypeName(Constants.CM_CONTAINS);
        link.setName(name);
        link.setHard(true);
        folder.getAssociations().add(link);

        return folder;
    }

    private void validateAssociations(ActiveNode node, Map<String,ActiveNode> nodeMap, Collection<? extends LinkItem> associations) {
        final Set<String> nameSet = new HashSet<>();
        for (LinkItem association : associations) {
            if (association.getRelationship() == null) {
                association.setRelationship(RelationshipKind.PARENT);
            }

            if (association.getTypeName() == null) {
                association.setTypeName(CM_CONTAINS);
            }

            if (StringUtils.isBlank(association.getName())) {
                throw new BadRequestException("Missing association name");
            }

            ActiveNode vertex = nodeMap.get(association.getVertexUUID());
            switch (association.getRelationship()) {
                case PARENT -> {
                    nodeValidator.validateAssociation(vertex, node, association.getTypeName());

                    if (association.getName() == null) {
                        Optional.ofNullable(node.getProperties().get(CM_NAME)).map(Object::toString).ifPresent(association::setName);
                    }

                    nodeValidator.validateAssociationName(association.getName());
                }

                case SOURCE -> {
                    nodeValidator.validateAssociation(vertex, node, association.getTypeName());
                    association.setName(null);
                }
                case CHILD -> {
                    nodeValidator.validateAssociation(node, vertex, association.getTypeName());

                    if (association.getName() == null) {
                        Optional.ofNullable(vertex.getData().getProperties().get(CM_NAME)).map(Object::toString).ifPresent(association::setName);
                    }

                    if (nameSet.contains(association.getName())) {
                        throw new BadRequestException("Duplicate name in the association list: two or mode children cannot be linked with the same name");
                    }

                    nameSet.add(association.getName());
                    nodeValidator.validateAssociationName(association.getName());
                }

                case TARGET -> {
                    nodeValidator.validateAssociation(node, vertex, association.getTypeName());
                    association.setName(null);
                }
            } // end switch
        } // end for
    }

    private long computeDescendants(ActiveNode child, ActiveNode parent) {
        try {
            var nodePath = pathDAO.findNodePathByNodeId(child.getId(), parent != null ? Optional.ofNullable(parent.getId()) : Optional.empty()).orElse(null);
            if (nodePath == null) {
                log.trace("No path found for node {} since it is new", child.getId());
                return 0;
            }
            nodePath = nodePath.replace(":", "\\:");

            var pageable = new Pageable();
            pageable.setPage(0);
            pageable.setSize(0);
            var p = searchPort.findNodes("NODEPATH:" + nodePath + "*", List.of(), pageable);
            return p.getTotalElements();
        } catch (IOException | SearchEngineException e) {
            log.error("Cannot retrieve descendants of node {} on Solr: {}", child.getUuid(), e.getMessage());
            throw new SystemException(e);
        }
    }
}
