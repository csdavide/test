package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.async.CompletableAsyncOperation;
import it.doqui.libra.librabl.business.provider.async.LinkTask;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.provider.data.dao.AssociationDAO;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.provider.mappers.NodeMapper;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.AssociationService;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.async.CompletedAsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.AssociationItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
@Slf4j
public class AssociationManager implements AssociationService {

    @Inject
    TransactionService txManager;

    @Inject
    TaskProducer producer;

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    LinkManager linkManager;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    SimpleNodeAccessManager simpleNodeAccessManager;

    @Inject
    AssociationDAO associationDAO;

    @Override
    public Paged<AssociationItem> findAssociations(String uuid, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable) {
        var node = simpleNodeAccessManager.getNode(uuid, null);
        return associationDAO
            .findAssociations(node, relationship, filterAssociationTypes, filterNodeTypes, pageable)
            .map(a -> nodeMapper.map(a));
    }

    @Override
    public AsyncOperation<AssociationItem> linkNode(String uuid, LinkItemRequest linkItem, OperationMode mode) {
        if (mode == OperationMode.ASYNC) {
            if (linkItem.isHard()) {
                switch (linkItem.getRelationship()) {
                    case PARENT, CHILD:
                        break;
                    default:
                        throw new BadRequestException("Invalid relationship: cannot be an hard association");
                }
            }

            var edges = linkManager.getParentChildNodes(uuid, linkItem);
            var parent = edges.getLeft();
            var child = edges.getRight();
            var linkTask = new LinkTask();
            linkTask.setTaskId(UUID.randomUUID().toString());
            linkTask.setAuthorityRef(UserContextManager.getContext().getAuthorityRef());
            linkTask.setChild(child.getUuid());
            linkTask.setParent(parent.getUuid());
            linkTask.setType(linkItem.getTypeName());
            linkTask.setName(linkItem.getName());
            linkTask.setHard(linkItem.isHard());
            linkTask.setRelationship(
                switch (linkItem.getRelationship()) {
                    case PARENT, CHILD -> "PARENT";
                    case SOURCE, TARGET -> "SOURCE";
                }
            );
            linkTask.setQueueName(asyncConfig.operations().queue());

            var request = new HashMap<String, Object>();
            request.put("uuid", uuid);
            request.put("link", linkItem);
            var task = asyncOperationService.registerTask(linkTask.getTaskId(), Map.of("request", request));

            try {
                producer.submit(linkTask);
                log.info("Async link operation submitted with taskId {}", linkTask.getTaskId());
                var result = new CompletableAsyncOperation<AssociationItem>(linkTask.getTaskId());
                result.setCreatedAt(task.getCreatedAt());
                result.setUpdatedAt(task.getUpdatedAt());
                return result;
            } catch (RuntimeException e) {
                log.error("Failed to submit async operation {}: {}", linkTask.getTaskId(), e.getMessage());
                asyncOperationService.removeTask(linkTask.getTaskId());
                return new CompletedAsyncOperation<>(AsyncOperation.Status.FAILED, null);
            }

        } else {
            return txManager.perform(tx -> {
                var counter = new AtomicLong(0);
                var r = linkManager.createLink(tx, uuid, linkItem, counter);
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
    public void unlinkNode(@NotNull String uuid, @NotNull LinkItemRequest linkItem) {
        txManager.perform(tx -> {
            AtomicLong counter = new AtomicLong(0);
            linkManager.removeLink(tx, uuid, linkItem, counter);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(Set.of(uuid, linkItem.getVertexUUID()))
                .count(counter.get())
                .build();
        });
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
