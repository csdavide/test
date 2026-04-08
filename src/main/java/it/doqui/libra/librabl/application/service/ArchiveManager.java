package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.application.ports.in.ArchiveUseCase;
import it.doqui.libra.librabl.domain.model.graph.Constants;
import it.doqui.libra.librabl.application.mappers.AssociationMapper;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.ArchiveDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.AssociationDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeLoaderDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.QueryContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ArchivedNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.Association;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static it.doqui.libra.librabl.domain.model.graph.Constants.ASPECT_SYS_ARCHIVED;

@ApplicationScoped
@Slf4j
public class ArchiveManager implements ArchiveUseCase {

    @Inject
    ArchiveDAO archiveDAO;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

    @Inject
    AssociationDAO associationDAO;

    @Inject
    LinkManager linkManager;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    AssociationMapper associationMapper;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Override
    public void restoreNode(Vertex node, ParentLink destination, LinkMode mode) {
        transactionManagerPort.perform(tx -> {
            var counter = new AtomicLong(0);
            var n = restoreNode(tx, node, destination, mode, counter);
            sessionContext.getOperationCounter().set(counter.get());
            return PerformResult.<Long>builder()
                .result(tx.getId())
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(n.getUuid()))
                .count(counter.get())
                .build();
        });
    }

    private ArchivedNode restoreNode(ApplicationTransaction tx, Vertex vertex, ParentLink destination, LinkMode mode, AtomicLong counter) {
        var node = archiveDAO.getNode(vertex)
            .map(n -> {
                if (n.isNodeUndefined()) {
                    throw new ForbiddenException("Permission " + PermissionFlag.R + " is required on node " + vertex.getValue());
                }
                return n;
            })
            .orElseThrow(() -> new NotFoundException("Node not found in the archive: " + vertex));


        if (!node.getAspects().contains(ASPECT_SYS_ARCHIVED)) {
            throw new PreconditionFailedException("Node " + vertex.getValue() + " is not restorable: missing archived aspect");
        }

        final var restoreAllPreviousParentAssociations = destination == null;
        if (restoreAllPreviousParentAssociations) {
            destination = archiveDAO.findRestorableParentAssociation(node.getId())
                .orElseThrow(() -> new PreconditionFailedException("Node " + vertex.getValue() + " is not restorable: missing parent"));

            var qc = QueryContext.builder()
                    .schema(sessionContext.getUserContext().getDbSchema())
                    .tenant(sessionContext.getTenant())
                    .optionSet(Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS))
                    .scope(QueryScope.DEFAULT)
                    .build();
            var parent = nodeLoaderDAO
                .getNode(destination.getParent(), qc)
                .orElseThrow(() -> new PreconditionFailedException("Node " + vertex.getValue() + " is not restorable: missing parent"));

            permissionValidator.requirePermission(parent, PermissionFlag.C);

            if (mode == null) {
                mode = LinkMode.ALL;
            }
        }

        // restore all nodes deleted in the same transaction using the new transaction
        // and related incoming associations
        archiveDAO.restoreTx(node, tx.getId(), counter);

        if (restoreAllPreviousParentAssociations) {
            archiveDAO.restoreFirstNodeAssociations(node, mode);
        } else {
            linkManager.createParentLink(tx, new Vertex(VertexType.UUID, node.getUuid()), destination, false, counter);
        }

        associationDAO.rebuildPathsWhereNodeTx(tx.getId(),null);

        Consumer<Association> consumer = association -> {
            var link = new LinkItemRequest();
            link.setTypeName(association.getTypeName());
            link.setName(association.getName());
            link.setHard(association.isHard());
            link.setVertexUUID(association.getParent().getUuid());
            link.setRelationship(RelationshipKind.PARENT);

            try {
                var r = associationMapper.relationship(association.getChild().getUuid(), link);
                linkManager.createParentLink(tx, r.vertex(), r.parentLink(), r.createMissingPath(), counter);
            } catch (ForbiddenException e) {
                log.warn("Unable to restore secondary link for restored node {} to parent {} (tenant {})", node.getUuid(), link.getVertexUUID(), node.getTenant());
            }
            //TODO: ottimizzare con la createLinks
        };

        transactionManagerPort.doAsAdmin(() -> {
            archiveDAO.retrieveOutgoingAssociations(tx.getId(), consumer);
            archiveDAO.retrieveOutgoingEmbeddedAssociations(node.getTx().getId(), tx.getId(), consumer);
            return null;
        });

        archiveDAO.deleteArchivedNodesAndAssociations(node.getTx().getId());
        return node;
    }

    @Override
    public Optional<NodeItem> getNode(Vertex node, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        return archiveDAO.getNode(node)
            .map(n -> {
                if (n.isNodeUndefined()) {
                    throw new ForbiddenException("Permission " + PermissionFlag.R + " is required on node " + node.getValue());
                }
                if (optionSet.contains(MapOption.PARENT_ASSOCIATIONS)) {
                    n.setParents(archiveDAO.findParentAssociations(List.of(n.getId()), false).get(n.getId()));
                } else if (optionSet.contains(MapOption.PARENT_HARD_ASSOCIATIONS)) {
                    n.setParents(archiveDAO.findParentAssociations(List.of(n.getId()), true).get(n.getId()));
                }
                return nodeMapper.asNodeItem(n, optionSet, filterPropertyNames, locale);
            });
    }

    @Override
    public Paged<NodeItem> findNodes(
        Collection<String> uuid, Collection<String> types, Collection<String> aspects, boolean includeMetadata,
        Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, boolean excludeDescendants, Pageable pageable) {

        var pagedNodes = archiveDAO.findNodes(uuid, types, aspects, includeMetadata, excludeDescendants, pageable);

        if (optionSet.contains(MapOption.PARENT_ASSOCIATIONS) || optionSet.contains(MapOption.PARENT_HARD_ASSOCIATIONS)) {
            boolean hardOnly = optionSet.contains(MapOption.PARENT_HARD_ASSOCIATIONS) && !optionSet.contains(MapOption.PARENT_ASSOCIATIONS);
            var nodesIds = pagedNodes.getItems().stream().map(ArchivedNode::getId).toList();
            var parentAssociationMap = archiveDAO.findParentAssociations(nodesIds, hardOnly);
            for (var n : pagedNodes.getItems()) {
                n.setParents(parentAssociationMap.getOrDefault(n.getId(), List.of()));
            }
        }
        return pagedNodes.map(n -> nodeMapper.asNodeItem(n, optionSet, filterPropertyNames, locale));
    }

    @Override
    public void purgeNode(Vertex node, boolean remove) {
        transactionManagerPort.perform(tx -> {
            AtomicLong counter = new AtomicLong(0);

            var child = archiveDAO.getNode(node)
                .map(n -> {
                    if (n.isNodeUndefined()) {
                        throw new ForbiddenException("Permission " + PermissionFlag.R + " is required on node " + node.getValue());
                    }
                    permissionValidator.requirePermission(n, PermissionFlag.D, true);
                    return n;
                })
                .orElseThrow(NotFoundException::new);
            var archivedAssociations = archiveDAO.findParentAssociations(List.of(child.getId()), true).get(child.getId());

            boolean isClosureRoot = child.getAspects().contains(Constants.ASPECT_SYS_ARCHIVED);
            long oldTx = child.getTx().getId();
            long newTx = tx.getId();
            long rootId = child.getId();

            archiveDAO.deleteArchivedAssociations(archivedAssociations, oldTx, newTx, rootId, isClosureRoot, remove, counter);
            return PerformResult.<Long>builder()
                .result(tx.getId())
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(child.getUuid()))
                .count(counter.get())
                .build();
        });
    }
}
