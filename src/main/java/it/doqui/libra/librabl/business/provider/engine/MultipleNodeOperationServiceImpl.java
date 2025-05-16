package it.doqui.libra.librabl.business.provider.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.api.v2.rest.dto.QueryParameters;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.TxDAO;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.exceptions.SearchEngineException;
import it.doqui.libra.librabl.business.service.interfaces.MultipleNodeOperationService;
import it.doqui.libra.librabl.business.service.interfaces.ReindexService;
import it.doqui.libra.librabl.business.service.interfaces.SearchService;
import it.doqui.libra.librabl.business.service.interfaces.VersionService;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.async.CompletedAsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.LimitExceededException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.messaging.MessageType.MULTINODE;
import static it.doqui.libra.librabl.views.OperationMode.ASYNC;
import static it.doqui.libra.librabl.views.OperationMode.SYNC;

@ApplicationScoped
@Slf4j
public class MultipleNodeOperationServiceImpl implements MultipleNodeOperationService {

    @Inject
    ReindexService reindexService;

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NodeManager nodeManager;

    @Inject
    LinkManager linkManager;

    @Inject
    ArchiveManager archiveManager;

    @Inject
    SearchService searchService;

    @Inject
    VersionService versionService;

    @Inject
    TxDAO txDAO;

    @Inject
    NodeDAO nodeDAO;

    @Override
    public AsyncOperation<?> performOperations(Collection<NodeOperation> operations, Long limit, OperationMode mode) {
        return performOperations(operations, limit, mode, 0);
    }

    @Override
    public AsyncOperation<?> performOperations(Collection<NodeOperation> operations, Long limit, OperationMode mode, long delay) {
        if (mode == OperationMode.AUTO && isSyncApplicable(operations)) {
            mode = OperationMode.SYNC;
        }

        log.debug("Performing mode {}", mode);
        return switch (mode) {
            case SYNC -> {
                var r = performOperations(operations, limit);
                yield new CompletedAsyncOperation<>(r);
            }
            case ASYNC, AUTO -> asyncOperationService.submit(MULTINODE, null, operations, delay);
        };
    }

    private Collection<NodeOperationResponse> performOperations(Collection<NodeOperation> operations, Long limit) {
        var responses = new ArrayList<NodeOperationResponse>();
        TransactionService.current().requireNew(() -> {
            TransactionService.current().options().disableWithInTxMode();
            var remainingLimit = limit;
            for (var operation : operations) {
                var counter = new AtomicLong(0);
                var r = TransactionService.current().performNew(tx -> {
                    var response = performOperation(tx, operation, counter);
                    return PerformResult.<NodeOperationResponse>builder().mode(PerformResult.Mode.SYNC).result(response).build();
                });

                if (remainingLimit != null) {
                    remainingLimit -= counter.get();
                    if (remainingLimit < 0) {
                        throw new LimitExceededException();
                    }
                }

                responses.add(r);
            }
            return null;
        });

        return responses;
    }

    private NodeOperationResponse performOperation(ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        var response = new NodeOperationResponse();
        response.setOp(operation.getOp());

        switch (operation.getOp()) {
            case REINDEX:
                if (operation.getUuid() != null) {
                    log.debug("Performing operation {} of node uuid {}", operation.getOp(), operation.getUuid());
                    if (operation.getOperand() != null) {
                        log.warn("Ignoring operand because node uuid has a value");
                    }
                    reindex(operation.getUuid());
                } else if (operation.getOperand() != null) {
                    log.debug("Performing operation {} with operand {}", operation.getOp(), operation.getOperand().getClass().getSimpleName());
                    var tenantRef = UserContextManager.getContext().getTenantRef();
                    if (operation.getOperand() instanceof Number n) {
                        reindexService.reindex(tenantRef, n.longValue(), ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                    } else if (operation.getOperand() instanceof String s) {
                        reindexService.reindex(tenantRef, Long.parseLong(s), ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                    } else if (operation.getOperand() instanceof Collection<?> collection) {
                        var transactions = collection.stream().map(Object::toString).collect(Collectors.toList());
                        reindexService.reindex(tenantRef, transactions, ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                    } else {
                        log.warn("Invalid operand type {}", operation.getOperand().getClass());
                    }
                }
                break;

            case UPDATE: {
                var input = objectMapper.convertValue(operation.getOperand(), InputNodeRequest.class);
                nodeManager.updateNode(operation.getUuid(), input, ObjectUtils.valueOf(OperationOption.class, operation.getOptions()));
                response.setResult(new NodeInfoItem(null, null, operation.getUuid(), null));
                counter.incrementAndGet();
                break;
            }

            case UPDATE_WHERE: {
                var operand = objectMapper.convertValue(operation.getOperand(), ConditionalUpdateRequest.class);
                var queryParameters = new QueryParameters();
                queryParameters.setUuids(operand.getUuids());
                queryParameters.setQ(operand.getQ());
                queryParameters.setPath(operand.getPath());

                int count = updateNodes(queryParameters, operand.getInput(), ObjectUtils.valueOf(OperationOption.class, operation.getOptions()));
                counter.addAndGet(count);
                response.setResult(count);
                break;
            }

            case VERSION: {
                final String tag;
                if (operation.getOperand() == null) {
                    tag = null;
                } else if (operation.getOperand() instanceof String s) {
                    tag = s;
                } else {
                    throw new BadRequestException("Invalid tag specified as operand in " + operation.getOp() + " operation");
                }

                versionService.createNodeVersion(operation.getUuid(), tag).ifPresent(response::setResult);
                break;
            }

            case REPLACE: {
                var operand = objectMapper.convertValue(operation.getOperand(), NodeOperation.ReplaceOperand.class);
                versionService.replaceNodeMetadata(operation.getUuid(), operand.getUuid(), operand.getVersion());

                int count = 1;
                counter.addAndGet(count);
                response.setResult(count);
                break;
            }

            case DELETE_WHERE: {
                var operand = objectMapper.convertValue(operation.getOperand(), ConditionalDeleteRequest.class);
                var queryParameters = new QueryParameters();
                queryParameters.setUuids(operand.getUuids());
                queryParameters.setQ(operand.getQ());
                queryParameters.setPath(operand.getPath());

                int count = deleteNodes(queryParameters, operand.getMode());
                counter.addAndGet(count);
                response.setResult(count);
                break;
            }

            case CREATE: {
                var input = objectMapper.convertValue(operation.getOperand(), LinkedInputNodeRequest.class);
                var uuid = nodeManager.createNode(input);
                response.setResult(new NodeInfoItem(null, null, uuid, null));
                counter.incrementAndGet();
                break;
            }

            case COPY: {
                response.setResult(new NodeInfoItem(null, null, performCopy(tx, operation, counter), null));
                break;
            }

            case LINK: {
                performLink(tx, operation, counter);
                break;
            }

            case UNLINK: {
                performUnlink(tx, operation, counter);
                break;
            }

            case MOVE: {
                performMove(tx, operation, counter);
                break;
            }

            case RENAME: {
                performRename(tx, operation, counter);
                break;
            }

            case RESTORE: {
                performRestore(tx, operation, counter);
                break;
            }

            case DELETE: {
                var deleteMode = DeleteMode.DELETE;
                if (operation.getOperand() != null) {
                    NodeOperation.DeleteOperand rop = objectMapper.convertValue(operation.getOperand(), NodeOperation.DeleteOperand.class);
                    if (rop.getMode() != null) {
                        deleteMode = rop.getMode();
                    }
                }
                performDelete(tx, operation.getUuid(), deleteMode, counter);
                break;
            }

            default:
                throw new RuntimeException("Unsupported operation " + operation.getOp());
        }

        return response;
    }

    @Override
    public int updateNodes(QueryParameters queryParameters, InputNodeRequest input, Set<OperationOption> optionSet) {
        return findNodesAndPerform(queryParameters, uuids -> {
            int c = 0;
            for (String uuid : uuids) {
                nodeManager.updateNode(uuid, input, optionSet);
                c++;
            }
            return c;
        });
    }

    @Override
    public int deleteNodes(QueryParameters queryParameters, DeleteMode mode) {
        return findNodesAndPerform(queryParameters, uuids -> {
            int c = 0;
            for (String uuid : uuids) {
                nodeManager.deleteNode(uuid, mode);
                c++;
            }
            return c;
        });
    }

    @Override
    public int findNodesAndPerform(QueryParameters queryParameters, Function<Collection<String>, Integer> f) {
        return TransactionService.current().perform(tx -> {
            int count = 0;
            if (queryParameters.getUuids() != null) {
                if (f != null) {
                    count += f.apply(queryParameters.getUuids());
                }
            } else {
                var q = queryParameters.getQ();
                if (q == null) {
                    if (queryParameters.getPath() == null) {
                        throw new BadRequestException("No query parameter specified");
                    }

                    q = String.format("PATH:\"%s\"", queryParameters.getPath());
                }

                try {
                    var pageable = new Pageable();
                    pageable.setPage(0);
                    pageable.setPage(100);
                    Paged<String> p;
                    do {
                        p = searchService.findNodes(q, null, pageable);
                        if (f != null) {
                            count += f.apply(queryParameters.getUuids());
                        }

                        pageable.setPage(pageable.getPage() + 1);
                    } while (pageable.getPage() < p.getTotalPages());
                } catch (SearchEngineException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return PerformResult.<Integer>builder().result(count).build();
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.MANAGEMENT)
    void reindex(String uuid) {
        var tenantRef = UserContextManager.getContext().getTenantRef();
        txDAO.findTransactionOfNode(uuid)
            .ifPresentOrElse(
                tx -> reindexService.reindex(tenantRef, tx.getId(), ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5)),
                () -> log.warn("Node not found: {}", uuid)
            );
    }

    void performLink(ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        var association = Optional.ofNullable(operation.getAssociation()).orElseThrow(() -> new BadRequestException("No association"));
        linkManager.createLink(tx, operation.getUuid(), new LinkItemRequest(association), counter);
    }

    void performUnlink(ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        linkManager.removeLink(tx, operation.getUuid(), new LinkItemRequest(operation.getAssociation()), counter);
    }

    void performDelete(ApplicationTransaction tx, String uuid, DeleteMode deleteMode, AtomicLong counter) {
        linkManager.removeAllLinks(tx, uuid, deleteMode, counter, Set.of());
    }

    void performRestore(ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        LinkItemRequest destination = null;
        if (operation.getAssociation() != null) {
            if (!operation.getAssociation().isHard()) {
                throw new BadRequestException("Cannot restore on a soft link");
            }

            if (operation.getAssociation().getRelationship() != RelationshipKind.PARENT) {
                throw new BadRequestException("Restore requires a parent relationship");
            }

            destination = new LinkItemRequest(operation.getAssociation());
        }

        var mode = LinkMode.ALL;
        if (operation.getOperand() instanceof NodeOperation.RestoreOperand operand) {
            mode = operand.getMode();
        }

        archiveManager.restoreNode(tx, operation.getUuid(), destination, mode, counter);
    }

    void performMove(ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        if (!(operation.getOperand() instanceof String destinationUUID)) {
            throw new BadRequestException("No target specified as operand in " + operation.getOp() + " operation");
        }

        var source = Optional.ofNullable(operation.getAssociation()).map(LinkItemRequest::new).orElse(null);
        var destination = source == null ? new LinkItemRequest() : new LinkItemRequest(source);
        destination.setVertexUUID(destinationUUID);
        linkManager.moveLink(tx, operation.getUuid(), source, destination, counter);
    }

    void performRename(@TraceParam(ignore = true) ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        var association = Optional.ofNullable(operation.getAssociation()).orElseThrow(() -> new BadRequestException("No association"));
        if (StringUtils.isBlank(association.getName())) {
            throw new BadRequestException("No name specified in " + operation.getOp() + " operation");
        }

        if (operation.getOperand() != null) {
            NodeOperation.RenameOperand rop = objectMapper.convertValue(operation.getOperand(), NodeOperation.RenameOperand.class);
            if (rop.getMode() != null && association.getVertexUUID() != null) {
                throw new BadRequestException("Conflict found: specified generic and specific target node.");
            }

            var node = nodeDAO.findNodeByUUID(operation.getUuid(), Set.of(MapOption.DEFAULT, MapOption.PARENT_ASSOCIATIONS), QueryScope.DEFAULT)
                .orElseThrow(() -> new NotFoundException(operation.getUuid()));
            if (rop.getPropertyName() != null) {

                var name = PrefixedQName.valueOf(association.getName()).getLocalPart();
                if (!StringUtils.equals(ObjectUtils.getAsString(node.getProperties().get(rop.getPropertyName())), name)) {
                    var input = new InputNodeRequest();
                    input.getProperties().put(rop.getPropertyName(), name);
                    nodeManager.updateNode(tx, node, input, Set.of());
                }
            }

            if (association.getVertexUUID() == null && rop.getMode() != null) {
                linkManager.renameLinks(tx, node, rop.getMode(), association.getName(), counter);
            }
        }

        if (association.getVertexUUID() != null) {
            linkManager.renameLink(tx, operation.getUuid(), new LinkItemRequest(operation.getAssociation()), association.getName(), counter);
        }
    }

    String performCopy(ApplicationTransaction tx, NodeOperation operation, AtomicLong counter) {
        var cp = objectMapper.convertValue(operation.getOperand(), NodeOperation.CopyOperand.class);
        var node = nodeManager.copyNode(tx, operation.getUuid(), new LinkItemRequest(operation.getAssociation()), cp.isCopyChildren(), !cp.isExcludeAssociations(), cp.getCopyMode(), counter);
        return node.getUuid();
    }

    private boolean isSyncApplicable(Collection<NodeOperation> operations) {
        return operations.stream()
            .filter(operation -> operation.getOp() != null)
            .map(operation -> operation.getOp() == NodeOperation.NodeOperationType.REINDEX ? SYNC : ASYNC)
            .filter(mode -> mode != SYNC)
            .findAny()
            .isEmpty();
    }

}
