package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.AssociationDAO;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.PathDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.Association;
import it.doqui.libra.librabl.business.provider.mappers.NodeMapper;
import it.doqui.libra.librabl.business.provider.mappers.PropertyConverter;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.interfaces.NodeService;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.association.LinkItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.DeleteMode;
import it.doqui.libra.librabl.views.node.DeleteOptions;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.schema.PropertyDescriptor;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.PATH_FLAG;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_CONTAINS;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_NAME;

@ApplicationScoped
@Slf4j
public class LinkManager {

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
    NodeService nodeService;

    @Inject
    RenditionManager renditionManager;

    void renameLink(ApplicationTransaction tx, String uuid, LinkItemRequest item, String name, AtomicLong counter) {
        var p = getParentChildNodes(uuid, item);
        renameLink(tx, p.getLeft(), p.getRight(), name, counter);
    }

    void renameLink(ApplicationTransaction tx, String parentUUID, String childUUID, String name, AtomicLong counter) {
        var nodeMap = nodeDAO.mapNodesInUUIDs(List.of(parentUUID, childUUID), Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT);
        var parent = Optional.ofNullable(nodeMap.get(parentUUID)).orElseThrow(() -> new NotFoundException(parentUUID));
        var child = Optional.ofNullable(nodeMap.get(childUUID)).orElseThrow(() -> new NotFoundException(childUUID));

        renameLink(tx, parent, child, name, counter);
    }

    void renameLinks(ApplicationTransaction tx, String uuid, LinkMode mode, String name, AtomicLong counter) {
        var node = nodeDAO
            .findNodeByUUID(uuid, Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT)
            .orElseThrow(() -> new NotFoundException(uuid));
        renameLinks(tx, node, mode, name, counter);
    }

    void renameLinks(ApplicationTransaction tx, ActiveNode node, LinkMode mode, String name, AtomicLong counter) {
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
            .filter(a -> !StringUtils.equals(a.getName(), name))
            .toList();

        if (!associations.isEmpty()) {
            var nodeMap = nodeDAO.mapNodesInUUIDs(
                associations.stream().map(a -> a.getParent().getUuid()).toList(),
                Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS),
                QueryScope.DEFAULT);

            for (var association : associations) {
                renameLink(tx, nodeMap.get(association.getParent().getUuid()), node, name, counter);
            }
        }
    }

    private void renameLink(ApplicationTransaction tx, ActiveNode parent, ActiveNode child, String name, AtomicLong counter) {
        var association = child.getParents().stream()
            .filter(a -> Objects.equals(a.getParent().getId(), parent.getId()))
            .findAny()
            .orElseThrow(() -> new NotFoundException(String.format("%s->%s", parent.getUuid(), child.getUuid())));

        permissionValidator.requirePermission(parent, PermissionFlag.C);
        permissionValidator.requirePermission(child, PermissionFlag.W);
        associationDAO.renameAssociation(association, tx.getId(), name, counter);
    }

    void moveLink(ApplicationTransaction tx, String uuid, LinkItemRequest source, LinkItemRequest destination, AtomicLong counter) {
        if (source != null && source.getRelationship() != null && source.getRelationship() != RelationshipKind.PARENT) {
            throw new BadRequestException("Incompatible source relationship " + source.getRelationship() + " in move operation");
        }
        if (destination.getRelationship() != null && destination.getRelationship() != RelationshipKind.PARENT) {
            throw new BadRequestException("Incompatible destination relationship " + destination.getRelationship() + " in move operation");
        }

        var list = new ArrayList<LinkItemRequest>();
        list.add(destination);
        if (source != null && (source.getVertexUUID() != null || source.getPath() != null)) {
            list.add(source);
        }

        var nodeMap = retrieveRelatedNodes(uuid, list, false, Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS));
        var node = nodeMap.get(uuid);

        var sourceParent = source != null ? nodeMap.get(source.getVertexUUID()) : null;
        if (sourceParent == null) {
            var sourceParentUUID = node.getParents().stream()
                .filter(Association::isHard)
                .filter(a -> source == null || source.getTypeName() == null || StringUtils.equals(source.getTypeName(), a.getTypeName()))
                .filter(a -> source == null || source.getName() == null || StringUtils.equals(source.getName(), a.getName()))
                .map(a -> {
                    if (destination.getName() == null) {
                        destination.setName(a.getName());
                    }
                    if (destination.getTypeName() == null) {
                        destination.setTypeName(a.getTypeName());
                    }
                    return a.getParent().getUuid();
                })
                .findAny()
                .orElseThrow(() -> new BadRequestException(String.format("Unable to find any source association for node %s and source %s", node.getUuid(), source)));

            sourceParent = nodeDAO
                .findNodeByUUID(sourceParentUUID, Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT)
                .orElseThrow(() -> new NotFoundException(sourceParentUUID));
        }

        var createdAssociations = createAssociations(tx, node, nodeMap, List.of(destination), counter);
        if (!createdAssociations.isEmpty()) {
            removeAssociation(
                tx,
                sourceParent,
                node,
                Optional.ofNullable(source).map(LinkItem::getTypeName).orElse(null),
                Optional.ofNullable(source).map(LinkItem::getName).orElse(null),
                counter,
                DeleteMode.DELETE,
                Set.of());
        }
    }

    Association createLink(ApplicationTransaction tx, String uuid, LinkItemRequest link, AtomicLong counter) {
        var node = nodeDAO
            .findNodeByUUID(uuid, Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT)
            .orElseThrow(() -> new NotFoundException(uuid));

        var association = createLinks(tx, node, List.of(link), false, counter)
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException(String.format("Unable to create link between %s and %s", uuid, link)));

        nodeDAO.setTransaction(association.getChild().getId(), tx.getId(), PATH_FLAG);
        if (counter != null) {
            counter.incrementAndGet();
        }

        return association;
    }

    List<Association> createLinks(ApplicationTransaction tx, ActiveNode node, Collection<LinkItemRequest> associations, boolean checkPrimary, AtomicLong counter) {
        var nodeMap = retrieveRelatedNodes(null, associations, checkPrimary, Set.of(MapOption.DEFAULT));
        return createAssociations(tx, node, nodeMap, associations, counter);
    }

    void removeLink(ApplicationTransaction tx, String uuid, LinkItemRequest item, AtomicLong counter) {
        var p = getParentChildNodes(uuid, item);
        removeAssociation(tx, p.getLeft(), p.getRight(), item.getTypeName(), item.getName(), counter, DeleteMode.DELETE, Set.of());
    }

    void removeAllLinks(ApplicationTransaction tx, String uuid, DeleteMode deleteMode, AtomicLong counter, Set<DeleteOptions> deleteOptions) {
        var node = nodeDAO
            .findNodeByUUID(uuid, Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT);

        if (node.isEmpty()) {
            if (deleteMode != DeleteMode.EXPIRED) {
                throw new NotFoundException(uuid);
            }

            log.debug("Node {} not found on tenant {} for deletion if expired", uuid, UserContextManager.getTenant());
        } else {
            removeAllLinks(tx, node.get(), deleteMode, counter, deleteOptions);
        }
    }

    private void removeAllLinks(ApplicationTransaction tx, ActiveNode node, DeleteMode deleteMode, AtomicLong counter, Set<DeleteOptions> deleteOptions) {
        if (deleteMode == DeleteMode.EXPIRED) {
            var pd = new PropertyDescriptor();
            pd.setName(Constants.PROP_ECMSYS_EXPIRES_AT);
            pd.setType(PropertyConverter.TYPE_DATETIME);
            if (propertyConverter.convertPropertyValue(pd, node.getProperties().get(Constants.PROP_ECMSYS_EXPIRES_AT)) instanceof ZonedDateTime expiresAt) {
                if (expiresAt.isAfter(ZonedDateTime.now())) {
                    log.debug("Node {} on tenant {} not yet expired: it will expire at {}", node.getUuid(), UserContextManager.getTenant(), expiresAt);
                    return;
                }
            } else {
                log.debug("Node {} on tenant {} has no expiration datetime", node.getUuid(), UserContextManager.getTenant());
                return;
            }
        }

        var links = node.getParents().stream().map(a  -> nodeMapper.mapParent(a)).map(LinkItemRequest::new).toList();
        var parentMap = retrieveRelatedNodes(null, links, false, Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS));

        var associations = new ArrayList<>(node.getParents().stream().filter(Association::isHard).toList());
        if (associations.isEmpty()) {
            log.debug("Deleting node {} without parents", node.getUuid());
            var a = new Association();
            a.setHard(true);
            a.setChild(node);
            associationDAO.deleteAssociation(a, tx.getId(), true, deleteMode, counter);
        } else {
            for (var i = associations.size() - 1; i >= 0; i--) {
                var item = associations.get(i);
                var parent = parentMap.get(item.getParent().getUuid());
                removeAssociation(tx, parent, node, item.getTypeName(), item.getName(), counter, deleteMode, deleteOptions);
            }
        }
    }

    Pair<ActiveNode,ActiveNode> getParentChildNodes(String uuid, LinkItemRequest item) {
        var nodeMap = retrieveRelatedNodes(uuid, List.of(item), false, Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS));
        var p = getParentChildPair(uuid, item);
        var parent = Optional.ofNullable(nodeMap.get(p.getLeft())).orElseThrow(() -> new NotFoundException(p.getLeft()));
        var child = Optional.ofNullable(nodeMap.get(p.getRight())).orElseThrow(() -> new NotFoundException(p.getRight()));

        return new ImmutablePair<>(parent, child);
    }

    private Pair<String,String> getParentChildPair(String uuid, LinkItem association) {
        final String parent;
        final String child = switch (association.getRelationship()) {
            case PARENT, SOURCE -> {
                parent = association.getVertexUUID();
                yield uuid;
            }
            case CHILD, TARGET -> {
                parent = uuid;
                yield association.getVertexUUID();
            }
            default -> throw new RuntimeException("Unexpected relationship " + association.getRelationship());
        };

        return new ImmutablePair<>(parent, child);
    }

    private void removeAssociation(ApplicationTransaction tx, ActiveNode parent, ActiveNode child, String associationType, String associationName, AtomicLong counter, DeleteMode deleteMode, Set<DeleteOptions> deleteOptions) {
        permissionValidator.requirePermission(parent, PermissionFlag.D);
        var association = child.getParents().stream()
            .filter(a -> Objects.equals(a.getParent().getId(), parent.getId()))
            .findFirst()
            .orElseThrow(() -> new PreconditionFailedException(String.format("Unable to find association between %s and %s", parent.getUuid(), child.getUuid())));

        if (associationType != null && !StringUtils.equals(association.getTypeName(), associationType)) {
            throw new PreconditionFailedException("Association has a different type than requested one: " + associationType);
        }

        if (associationName != null && !StringUtils.equals(association.getName(), associationName)) {
            throw new PreconditionFailedException("Association has a different name than requested one: " + associationName);
        }

        var archive = association.isHard() && child.getParents().stream().noneMatch(a -> a.isHard() && !Objects.equals(a.getId(), association.getId()));
        if (archive && !deleteOptions.contains(DeleteOptions.SKIP_RENDITION_CHECK)) {
            renditionManager.renditionableUpdates(tx, child, counter);
        }
        if (!associationDAO.deleteAssociation(association, tx.getId(), archive, deleteMode, counter)) {
            throw new RuntimeException("Unable to delete association " + association.getId());
        }
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
            var association = createAssociation(tx, parent, child, link.getTypeName(), link.getName(), hard, counter);
            associations.add(association);
        }

        return associations;
    }

    private Association createAssociation(ApplicationTransaction tx, ActiveNode parent, ActiveNode child, String type, String name, Boolean hard, AtomicLong counter) {
        boolean duplicatesAllowed = UserContextManager.getTenantData().map(TenantData::isDuplicatesAllowed).orElse(false);
        Association association = new Association();
        association.setParent(parent);
        association.setChild(child);
        association.setTypeName(type);
        association.setName(name);
        association.setHard(hard);
        association.setCode(name == null || duplicatesAllowed ? null : name.toLowerCase());

        associationDAO.createAssociation(tx, association, counter);
        return association;
    }

    private void requireNoCycle(ActiveNode parent, ActiveNode child) {
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

    Map<String,ActiveNode> retrieveRelatedNodes(String uuid, Collection<LinkItemRequest> associations, boolean checkPrimary, Set<MapOption> options) {
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
                    TransactionService.current().options().disableWithInTxMode();
                }
            } // end for

            if (checkPrimary && primaryParent == null) {
                log.debug("Missing primary parent relationship in {}", associations);
                throw new BadRequestException("No parent hard association specified");
            }
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

        var nodeMap = nodeDAO.mapNodesInUUIDs(uuids, options, QueryScope.DEFAULT);
        // check if all nodes have been found
        if (uuid != null && !nodeMap.containsKey(uuid)) {
            throw new NotFoundException(uuid);
        } else {
            final List<String> unknownUUIDs = linkMap.keySet().stream().filter(id -> !nodeMap.containsKey(id)).toList();
            if (!unknownUUIDs.isEmpty()) {
                throw new NotFoundException(String.join(",", unknownUUIDs));
            }
        }

        return nodeMap;
    }

    private String createPath(String path, Map<String, String> map) {
        log.debug("Creating path {}", path);
        String[] elements = path.split("/");
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
                    map.put(element, uuid);
                } else if (StringUtils.equals(element, longestPath)) {
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
}
