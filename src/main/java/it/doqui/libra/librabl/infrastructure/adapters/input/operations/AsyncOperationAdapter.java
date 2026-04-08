package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.NodeInfoItem;
import it.doqui.libra.librabl.application.mappers.AsyncOperationConverter;
import it.doqui.libra.librabl.application.model.async.CompletableAsyncOperation;
import it.doqui.libra.librabl.application.model.async.CompletedAsyncOperation;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.application.mappers.AssociationMapper;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.foundation.async.TxSupport;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.domain.model.graph.BindingType;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.application.model.jobs.requests.*;
import it.doqui.libra.librabl.application.model.jobs.responses.*;
import it.doqui.libra.librabl.application.model.query.QueryParameters;
import it.doqui.libra.librabl.application.model.statements.RenameStatement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ApplicationScoped
@Slf4j
public class AsyncOperationAdapter implements AsyncOperationService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    JobUseCase jobService;

    @Inject
    AssociationMapper associationMapper;

    @Inject
    SessionContext sessionContext;

    @Override
    public Optional<FeedbackAsyncOperation> getTask(String tenant, String taskId) {
        return Optional.ofNullable(jobService.getJob(taskId, tenant))
                .map(AsyncOperationConverter::map);
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationError {
        private final String message;
    }

    @Override
    public AsyncOperation<?> performOperations(Collection<NodeOperation> operations, OperationParameters params) {
        assert params != null;
        if (sessionContext.getMode() == SessionMode.SYNC) {
            validateOperations(operations);
        }

        var jobResponse = executeJob(operations, params);
        if (jobResponse.isDone()) {
            var result = new ArrayList<NodeOperationResponse>();
            boolean success = true;
            if (jobResponse.getResult() instanceof JobListResult jobListResult) {
                final var resList = new ArrayList<>(jobListResult.getJobs());
                int i = 0;
                for (var operation : operations) {
                    var res = resList.get(i);
                    var operationResponse = new NodeOperationResponse();
                    operationResponse.setOp(operation.getOp());

                    final Object operationResult;
                    if (res.getStatus() == JobStatus.FAILED) {
                        success = false;
                        operationResult = OperationError.builder().message(res.getMessage()).build();
                    } else {
                        operationResult = switch (operation.getOp()) {
                            case CREATE, COPY, CREATE_OR_UPDATE -> {
                                if (res.isCompleted() && res.getResult() instanceof UUIDResult uuidResult) {
                                    yield NodeInfoItem.builder().uuid(uuidResult.getUuid()).build();
                                }
                                yield null;
                            }

                            case UPDATE -> {
                                if (res.isCompleted()) {
                                    yield NodeInfoItem.builder().uuid(operation.getUuid()).build();
                                }
                                yield null;
                            }

                            case UPDATE_WHERE, DELETE_WHERE, REPLACE -> {
                                if (res.isCompleted() && res.getResult() instanceof CountResult countResult) {
                                    yield countResult.getAffectedNodes();
                                }
                                yield null;
                            }

                            case VERSION -> {
                                if (res.isCompleted() && res.getResult() instanceof VersionResult versionResult) {
                                    yield versionResult.getVersion();
                                }
                                yield null;
                            }

                            default -> null;
                        };
                    }

                    operationResponse.setResult(operationResult);
                    result.add(operationResponse);
                    i++;
                }
            }
            return new CompletedAsyncOperation<>(result, success && jobResponse.isCompleted() ? JobStatus.SUCCESS : JobStatus.FAILED);
        } else {
            var result = new CompletableAsyncOperation<Void>(jobResponse.getJobId());
            result.setStatus(jobResponse.getStatus(), jobResponse.getMessage());
            result.setCreatedAt(jobResponse.getCreatedAt());
            result.setUpdatedAt(jobResponse.getUpdatedAt());

            return result;
        }
    }

    @Override
    public void executeAsyncOperations(String taskId, Collection<NodeOperation> operations, OperationParameters params) {
        log.info("Processing async node operations of job {}", taskId);
        try {
            jobService.setJob(taskId, JobStatus.RUNNING, null);
            var jobResponse = executeJob(operations, params);
            jobService.setJob(taskId, jobResponse.getStatus(), null);
        } catch (NotFoundException e) {
            log.warn("Job {} not found", taskId);
        } catch (RuntimeException e) {
            log.error("Failed to process async node operations job {}", taskId, e);
            var message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            jobService.failJob(taskId, message);
        }
    }

    private JobResponse executeJob(Collection<NodeOperation> operations, OperationParameters params) {
        var jobListRequest = convertOperations(operations);
        jobListRequest.setMode(params.getMode());
        jobListRequest.setDelay(Duration.of(params.getDelay(), ChronoUnit.MILLIS));
        jobListRequest.setAtomic(Optional.ofNullable(params.getTxSupport()).orElse(TxSupport.REQUIRED) == TxSupport.REQUIRED);
        return jobService.executeJob(jobListRequest);
    }

    private void validateOperations(Collection<NodeOperation> operations) {
        Integer compatibilityGroup = null;
        for (var operation : operations) {
            var op = operation.getOp();
            if (!op.canSupportMultiple() && operations.size() > 1) {
                throw new BadRequestException("Operation " + op.name() + " does not support multiple operations");
            }

            if (compatibilityGroup == null) {
                compatibilityGroup = op.getCompatibilityGroup();
            } else if (compatibilityGroup != op.getCompatibilityGroup()) {
                throw new BadRequestException("Operation " + op.name() + " cannot be mixed with other operations of different compatibility group");
            }
        }
    }

    private JobListRequest convertOperations(Collection<NodeOperation> operations) {
        var jobListRequest = new JobListRequest();

        for (var operation : operations) {
            var op = operation.getOp();
            var jobRequest = switch (op) {
                case CREATE, CREATE_OR_UPDATE -> {
                    var create = objectMapper.convertValue(operation.getOperand(), LinkedInputNodeRequest.class);
                    var request = new CreateJobRequest();
                    request.setCreate(create);

                    var options = new HashSet<>(operation.getOptions());
                    if (op == NodeOperation.NodeOperationType.CREATE) {
                        options.add(OperationOption.FAIL_IF_PATH_EXISTS);
                    }
                    request.setOptions(options);

                    yield request;
                }

                case UPDATE -> {
                    var query = new QueryParameters();
                    query.setUuids(List.of(operation.getUuid()));
                    var update = objectMapper.convertValue(operation.getOperand(), InputNodeRequest.class);
                    var request = new UpdateJobRequest();
                    request.setOptions(operation.getOptions());
                    request.setUpdate(update);
                    request.setQuery(query);
                    yield request;
                }

                case UPDATE_WHERE -> {
                    var conditionalUpdateRequest = objectMapper.convertValue(operation.getOperand(), ConditionalUpdateRequest.class);
                    var query = new QueryParameters();
                    query.setQ(conditionalUpdateRequest.getQ());
                    query.setPath(conditionalUpdateRequest.getPath());
                    query.setUuids(conditionalUpdateRequest.getUuids());
                    var request = new UpdateJobRequest();
                    request.setOptions(operation.getOptions());
                    request.setUpdate(conditionalUpdateRequest.getInput());
                    request.setQuery(query);
                    yield request;
                }

                case DELETE -> {
                    var deleteOperand = objectMapper.convertValue(operation.getOperand(), NodeOperation.DeleteOperand.class);
                    var query = new QueryParameters();
                    query.setUuids(List.of(operation.getUuid()));
                    var request = new DeleteJobRequest();
                    request.setQuery(query);
                    request.setDeleteMode(deleteOperand.getMode());
                    yield request;
                }

                case DELETE_WHERE -> {
                    var conditionalDeleteRequest = objectMapper.convertValue(operation.getOperand(), ConditionalDeleteRequest.class);
                    var query = new QueryParameters();
                    query.setQ(conditionalDeleteRequest.getQ());
                    query.setPath(conditionalDeleteRequest.getPath());
                    query.setUuids(conditionalDeleteRequest.getUuids());
                    var request = new DeleteJobRequest();
                    request.setDeleteMode(conditionalDeleteRequest.getMode());
                    request.setQuery(query);
                    yield request;
                }

                case COPY -> {
                    var copyOperand = objectMapper.convertValue(operation.getOperand(), NodeOperation.CopyOperand.class);
                    var copy = new CopyJobRequest.CopyStatement();
                    copy.setCopyChildren(copyOperand.isCopyChildren());
                    copy.setExcludeAssociations(copyOperand.isExcludeAssociations());
                    copy.setCopyMode(copyOperand.getCopyMode());

                    var link = new ParentLink();
                    link.setType(operation.getAssociation().getTypeName());
                    link.setName(operation.getAssociation().getName());
                    link.setBindingType(BindingType.getBindingType(operation.getAssociation().isHard()));
                    link.setParent(new Vertex(VertexType.UUID, operation.getAssociation().getVertexUUID()));

                    var request = new CopyJobRequest();
                    request.setVertex(new Vertex(VertexType.UUID, operation.getUuid()));
                    request.setLink(link);
                    request.setCopy(copy);
                    yield request;
                }

                case MOVE -> {
                    var destination = Optional.ofNullable(operation.getAssociation()).map(associationMapper::mapLinkItem).orElse(new ParentLink());
                    if (operation.getOperand() != null) {
                        destination.setParent(new Vertex(VertexType.UUID, operation.getOperand().toString()));
                    }

                    var request = new MoveJobRequest();
                    request.setNode(new Vertex(VertexType.UUID, operation.getUuid()));
                    request.setDestination(destination);
                    yield request;
                }

                case LINK -> {
                    var request = new LinkJobRequest();
                    request.setNode(request.getNode());
                    request.setLink(request.getLink());
                    yield request;
                }

                case UNLINK -> {
                    var request = new UnlinkJobRequest();
                    request.setNode(request.getNode());
                    request.setUnlink(request.getUnlink());
                    yield request;
                }

                case RENAME -> {
                    var renameOperand = objectMapper.convertValue(operation.getOperand(), NodeOperation.RenameOperand.class);
                    var rename = new RenameStatement();
                    rename.setName(operation.getAssociation().getName());
                    rename.setPropertyName(renameOperand.getPropertyName());
                    if (StringUtils.isNotBlank(operation.getAssociation().getVertexUUID())) {
                        rename.setParent(new Vertex(VertexType.UUID, operation.getAssociation().getVertexUUID()));
                        rename.setRenameMode(RenameStatement.RenameMode.SPECIFIC_PARENT);
                    } else {
                        rename.setRenameMode(switch (renameOperand.getMode()) {
                            case FIRST -> RenameStatement.RenameMode.FIRST_PARENT;
                            case HARD -> RenameStatement.RenameMode.ALL_HARD_PARENTS;
                            case ALL -> RenameStatement.RenameMode.ALL_PARENTS;
                        });
                    }

                    var request = new RenameJobRequest();
                    request.setNode(new Vertex(VertexType.UUID, operation.getUuid()));
                    request.setRename(rename);
                    yield request;
                }

                case RESTORE -> {
                    var restoreOperand = objectMapper.convertValue(operation.getOperand(), NodeOperation.RestoreOperand.class);
                    var restore = new RestoreJobRequest.RestoreStatement();
                    var r = associationMapper.relationship(operation.getUuid(), new LinkItemRequest(operation.getAssociation()));
                    restore.setNode(r.vertex());
                    restore.setDestination(r.parentLink());
                    restore.setLinkMode(restoreOperand.getMode());
                    var request = new RestoreJobRequest();
                    request.setRestore(restore);
                    yield request;
                }

                case VERSION -> {
                    var version = new VersionJobRequest.VersionStatement();
                    version.setTag(Optional.ofNullable(operation.getOperand()).map(Object::toString).orElse(null));
                    var request = new VersionJobRequest();
                    request.setNode(new Vertex(VertexType.UUID, operation.getUuid()));
                    request.setVersion(version);
                    yield request;
                }

                case REPLACE -> {
                    var replaceOperand = objectMapper.convertValue(operation.getOperand(), NodeOperation.ReplaceOperand.class);
                    var replace = new ReplaceStatement();
                    replace.setNode(new Vertex(VertexType.UUID, replaceOperand.getUuid()));
                    replace.setVersion(replaceOperand.getVersion());
                    var request = new ReplaceJobRequest();
                    request.setNode(new Vertex(VertexType.UUID, operation.getUuid()));
                    request.setReplace(replace);
                    yield request;
                }
            };

            jobListRequest.getJobs().add(jobRequest);
        }

        return jobListRequest;
    }

}
