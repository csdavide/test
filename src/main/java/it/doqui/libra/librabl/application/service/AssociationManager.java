package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.model.async.CompletableAsyncOperation;
import it.doqui.libra.librabl.application.model.async.CompletedAsyncOperation;
import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.application.ports.in.AssociationUseCase;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.application.mappers.AssociationMapper;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.AssociationDAO;
import it.doqui.libra.librabl.application.model.association.AssociationItem;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.application.model.jobs.requests.LinkJobRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
@Slf4j
public class AssociationManager implements AssociationUseCase {

    @Inject
    TransactionManagerPort txManager;

    @Inject
    LinkManager linkManager;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    SimpleNodeAccessManager simpleNodeAccessManager;

    @Inject
    AssociationDAO associationDAO;

    @Inject
    JobUseCase jobService;

    @Inject
    AssociationMapper associationMapper;

    @Inject
    SessionContext sessionContext;

    @Override
    public Paged<AssociationItem> findAssociations(String uuid, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable) {
        var node = simpleNodeAccessManager.getNode(uuid, null);
        return associationDAO
            .findAssociations(node, relationship, filterAssociationTypes, filterNodeTypes, pageable)
            .map(a -> nodeMapper.map(a));
    }

    @Override
    public LinkResult linkNode(Vertex vertex, ParentLink link, boolean createMissingPath) {
        return txManager.perform(tx -> {
            var counter = new AtomicLong(0);
            var r = linkManager.createParentLink(tx, vertex, link, createMissingPath, counter);
            long count = counter.get();
            sessionContext.getOperationCounter().addAndGet(count);
            return PerformResult.<LinkResult>builder()
                    .result(new LinkResult(nodeMapper.map(r), count))
                    .mode(PerformResult.Mode.SYNC)
                    .priorityUUIDs(Set.of(r.getChild().getUuid(), r.getParent().getUuid()))
                    .count(count)
                    .build();
        });
    }

    @Override
    public long unlinkNode(Vertex vertex, ParentLink link) {
        return txManager.perform(tx -> {
            AtomicLong counter = new AtomicLong(0);
            var a = linkManager.removeParentLink(tx, vertex, link, counter);
            long count = counter.get();
            sessionContext.getOperationCounter().addAndGet(count);
            return PerformResult.<Long>builder()
                    .mode(PerformResult.Mode.SYNC)
                    .result(count)
                    .priorityUUIDs(Set.of(a.getChild().getUuid(), a.getParent().getUuid()))
                    .count(count)
                    .build();
        });
    }

    @Override
    public AsyncOperation<AssociationItem> linkNode(String uuid, LinkItemRequest linkItem, OperationMode mode) {
        var relationship = associationMapper.relationship(uuid, linkItem);
        if (mode == OperationMode.ASYNC) {
            if (linkItem.isHard()) {
                switch (linkItem.getRelationship()) {
                    case PARENT, CHILD:
                        break;
                    default:
                        throw new BadRequestException("Invalid relationship: cannot be an hard association");
                }
            }

            var linkJobRequest = new LinkJobRequest();
            linkJobRequest.setNode(relationship.vertex());
            linkJobRequest.setLink(relationship.parentLink());
            linkJobRequest.setMode(OperationMode.ASYNC);
            return new CompletableAsyncOperation<>(jobService.executeJob(linkJobRequest));
        } else {
            return txManager.perform(tx -> {
                var counter = new AtomicLong(0);
                var r = linkManager.createParentLink(tx, relationship.vertex(), relationship.parentLink(), relationship.createMissingPath(), counter);
                sessionContext.getOperationCounter().addAndGet(counter.get());
                var result = PerformResult.<AssociationItem>builder()
                    .result(nodeMapper.map(r))
                    .mode(PerformResult.Mode.SYNC)
                    .priorityUUIDs(Set.of(uuid, linkItem.getVertexUUID()))
                    .count(counter.get())
                    .build();

                return result.map(CompletedAsyncOperation::new);
            });
        }
    }

    @Override
    public AssociationItem findAssociation(String firstUUID, String secondUUID) {
        return associationDAO
            .findAssociation(firstUUID, secondUUID)
            .map(a -> nodeMapper.map(a))
            .orElseThrow(() -> new NotFoundException(String.format("%s<->%s", firstUUID, secondUUID)));
    }

    @Override
    public void renameAssociation(String parentUUID, String childUUID, String name) {
        txManager.perform(tx -> {
            AtomicLong counter = new AtomicLong(0);
            linkManager.renameLink(tx, parentUUID, childUUID, name, counter);

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(childUUID))
                .count(counter.get())
                .build();
        });
    }
}
