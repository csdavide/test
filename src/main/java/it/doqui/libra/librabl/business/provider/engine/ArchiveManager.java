package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.provider.data.dao.ArchiveDAO;
import it.doqui.libra.librabl.business.provider.data.dao.AssociationDAO;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.provider.data.entities.Association;
import it.doqui.libra.librabl.business.provider.mappers.NodeMapper;
import it.doqui.libra.librabl.business.service.interfaces.ArchiveService;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.node.NodeItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.ASPECT_SYS_ARCHIVED;

@ApplicationScoped
@Slf4j
public class ArchiveManager implements ArchiveService {

    @Inject
    ArchiveDAO archiveDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    AssociationDAO associationDAO;

    @Inject
    LinkManager linkManager;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    PermissionValidator permissionValidator;

    @Override
    public void restoreNode(String uuid, LinkItemRequest destination, LinkMode mode) {
        TransactionService.current().perform(tx -> {
            var counter = new AtomicLong(0);
            restoreNode(tx, uuid, destination, mode, counter);
            return PerformResult.<Long>builder()
                .result(tx.getId())
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(uuid))
                .count(counter.get())
                .build();
        });
    }

    void restoreNode(ApplicationTransaction tx, String uuid, LinkItemRequest destination, LinkMode mode, AtomicLong counter) {
        var node = archiveDAO.getNode(uuid)
            .orElseThrow(() -> new NotFoundException("Node not found in the archive: " + uuid));

        permissionValidator.requirePermission(node, PermissionFlag.R, null);
        if (!node.getAspects().contains(ASPECT_SYS_ARCHIVED)) {
            throw new PreconditionFailedException("Node " + uuid + " is not restorable: missing archived aspect");
        }

        final var restoreAllPreviousParentAssociations = destination == null;
        if (restoreAllPreviousParentAssociations) {
            destination = archiveDAO.findRestorableParentAssociation(node.getId())
                .map(LinkItemRequest::new)
                .orElseThrow(() -> new PreconditionFailedException("Node " + uuid + " is not restorable: missing parent"));

            var parent = nodeDAO
                .findNodeByUUID(destination.getVertexUUID(), Set.of(MapOption.DEFAULT, MapOption.SG, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT)
                .orElseThrow(() -> new PreconditionFailedException("Node " + uuid + " is not restorable: missing parent"));

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
            linkManager.createLink(tx, node.getUuid(), destination, null);
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
                linkManager.createLink(tx, association.getChild().getUuid(), link, counter);
            } catch (ForbiddenException e) {
                log.warn("Unable to restore secondary link for restored node {} to parent {} (tenant {})", node.getUuid(), link.getVertexUUID(), node.getTenant());
            }
            //TODO: ottimizzare con la createLinks
        };

        TransactionService.current().doAsAdmin(() -> {
            archiveDAO.retrieveOutgoingAssociations(tx.getId(), consumer);
            archiveDAO.retrieveOutgoingEmbeddedAssociations(node.getTx().getId(), tx.getId(), consumer);
            return null;
        });

        archiveDAO.deleteArchivedNodesAndAssociations(node.getTx().getId());
    }

    @Override
    public Optional<NodeItem> getNode(String uuid, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        return archiveDAO.getNode(uuid)
            .map(n -> {
                if (optionSet.contains(MapOption.PARENT_ASSOCIATIONS)) {
                    n.setParents(archiveDAO.findParentAssociations(n.getId(), false));
                } else if (optionSet.contains(MapOption.PARENT_HARD_ASSOCIATIONS)) {
                    n.setParents(archiveDAO.findParentAssociations(n.getId(), true));
                }
                return nodeMapper.asNodeItem(n, optionSet, filterPropertyNames, locale);
            });
    }

    @Override
    public Paged<NodeItem> findNodes(
        Collection<String> uuid, Collection<String> types, Collection<String> aspects, boolean includeMetadata,
        Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, boolean excludeDescendants, Pageable pageable) {
        return archiveDAO.findNodes(uuid, types, aspects, includeMetadata, excludeDescendants, pageable)
            .map(n -> {
                if (optionSet.contains(MapOption.PARENT_ASSOCIATIONS)) {
                    n.setParents(archiveDAO.findParentAssociations(n.getId(), false));
                } else if (optionSet.contains(MapOption.PARENT_HARD_ASSOCIATIONS)) {
                    n.setParents(archiveDAO.findParentAssociations(n.getId(), true));
                }
                return nodeMapper.asNodeItem(n, optionSet, filterPropertyNames, locale);
            });
    }

    @Override
    public void purgeNode(String uuid, boolean remove) {
        TransactionService.current().perform(tx -> {
            AtomicLong counter = new AtomicLong(0);

            var child = archiveDAO.getNode(uuid).orElseThrow(NotFoundException::new);
            var archivedAssociations = archiveDAO
                .findParentAssociations(child.getId(), true)
                .stream()
                .map(aa -> {
                    var n = nodeDAO.findNodeById(aa.getParentId(), Set.of(MapOption.DEFAULT), QueryScope.DEFAULT).orElse(null);
                    var an = archiveDAO.getNode(aa.getParentId()).orElse(null);

                    if (n == null && an == null) {
                        throw new NotFoundException("Node " + aa.getParentId() + " not found.");
                    } else if (an == null) {
                        permissionValidator.requirePermission(n, PermissionFlag.D, aa);
                        return aa;
                    } else if (n == null) {
                        permissionValidator.requirePermission(an, PermissionFlag.D, aa);
                        return aa;
                    } else {
                        throw new ConflictException("Node cannot be archived and active at the same time.");
                    }
                })
                .toList();

            boolean isClosureRoot = child.getAspects().contains(Constants.ASPECT_SYS_ARCHIVED);
            long oldTx = child.getTx().getId();
            long newTx = tx.getId();
            long rootId = child.getId();

            archiveDAO.deleteArchivedAssociations(archivedAssociations, oldTx, newTx, rootId, isClosureRoot, remove, counter);
            return PerformResult.<Long>builder()
                .result(tx.getId())
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(uuid))
                .count(counter.get())
                .build();
        });
    }
}
