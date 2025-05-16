package it.doqui.libra.librabl.business.provider.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import it.doqui.libra.librabl.business.provider.data.dao.AsyncOperationDAO;
import it.doqui.libra.librabl.business.provider.data.entities.AsyncOperationEntity;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.async.CompletedAsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.views.AbstractOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@ApplicationScoped
@Slf4j
public class AsyncOperationServiceImpl implements AsyncOperationService {

    @Inject
    AsyncOperationDAO asyncOperationDAO;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    TaskProducer producer;

    @Override
    public Optional<FeedbackAsyncOperation> getTask(String taskId) {
        return asyncOperationDAO
            .findByIdOptional(taskId)
            .filter(t -> StringUtils.equalsIgnoreCase(t.getTenant(), UserContextManager.getTenant()))
            .map(this::map);
    }

    private FeedbackAsyncOperation map(AsyncOperationEntity t) {
        var op = new FeedbackAsyncOperation();
        op.setOperationId(t.getId());
        op.setStatus(t.getStatus());
        op.setData(t.getData());
        op.setCreatedAt(t.getCreatedAt());
        op.setUpdatedAt(t.getUpdatedAt());

        return op;
    }

    @Override
    public void removeTask(String taskId) {
        removeTask(taskId, null);
    }

    @Override
    public FeedbackAsyncOperation registerTask(String taskId, Map<String, Object> attributes) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var task = new AsyncOperationEntity();
            task.setId(taskId);
            task.setTenant(UserContextManager.getContext().getTenantRef().toString());
            task.setStatus(AsyncOperation.Status.SUBMITTED);
            if (attributes != null) {
                task.getData().putAll(attributes);
            }

            asyncOperationDAO.persist(task);
            return map(task);
        });
    }

    @Override
    public void removeTask(String taskId, Function<FeedbackAsyncOperation, Boolean> filter) {
        QuarkusTransaction.requiringNew().call(() -> {
            var task = asyncOperationDAO.findByIdOptional(taskId).orElseThrow(() -> new NotFoundException("Task " + taskId + " not found"));
            if (task.getStatus() == AsyncOperation.Status.SUBMITTED) {
                throw new PreconditionFailedException("Task " + taskId + " is not yet completed");
            }

            if (filter != null) {
                if (!filter.apply(map(task))) {
                    throw new PreconditionFailedException("Task " + taskId + " cannot be removed");
                }
            }

            if (!asyncOperationDAO.deleteById(taskId)) {
                throw new NotFoundException("Task " + taskId + " not found");
            }

            return null;
        });
    }

    @Override
    public void completeTask(String taskId, AsyncOperation.Status status, Map<String, Object> attributes) {
        if (StringUtils.isBlank(taskId)) {
            return;
        }

        QuarkusTransaction.requiringNew().call(() -> {
            var task = asyncOperationDAO.findByIdOptional(taskId).orElseThrow(() -> new NotFoundException("Unable to find task " + taskId));
            switch (task.getStatus()) {
                case SUBMITTED:
                case RUNNING:
                    task.setStatus(status);
                    if (attributes != null) {
                        task.getData().putAll(attributes);
                    }

                    asyncOperationDAO.persist(task);
                    break;

                default:
                    throw new PreconditionFailedException(String.format("Task %s is already completed", taskId));
            }
            return null;
        });
    }

    @Override
    public AsyncOperation<Void> submit(String messageType, String uuid, Collection<? extends AbstractOperation<?>> operations, long delay) {
        var taskId = UUID.randomUUID().toString();
        var request = new HashMap<String,Object>();
        request.put("type", messageType);
        request.put("uuid", uuid);
        request.put("operations", operations);
        var registeredOp = registerTask(taskId, Map.of("request", request));

        try {
            var authority = UserContextManager.getContext().getAuthorityRef().toString();
            producer.submit(context -> {
                var message = context.createMessage();
                message.setJMSType(messageType);
                message.setStringProperty("taskId", taskId);
                message.setStringProperty("authority", authority);
                message.setStringProperty("uuid", uuid);

                if (operations != null) {
                    try {
                        var objectMapper = new ObjectMapper();
                        var s = objectMapper.writeValueAsString(operations);
                        message.setStringProperty("operations", s);
                    } catch (JsonProcessingException e) {
                        throw new SystemException(e);
                    }
                }

                if (delay > 0) {
                    message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + delay);
                }

                return message;
            }, asyncConfig.operations().queue());

            log.info("Async {} operation submitted with taskId {}", messageType, taskId);
            var result = new CompletableAsyncOperation<Void>(taskId);
            result.setCreatedAt(registeredOp.getCreatedAt());
            result.setUpdatedAt(registeredOp.getUpdatedAt());
            return result;
        } catch (RuntimeException e) {
            log.error("Failed to submit async {} operation {}: {}", messageType, taskId, e.getMessage());
            removeTask(taskId);
            return new CompletedAsyncOperation<>(AsyncOperation.Status.FAILED, null);
        }
    }

    @Override
    public AsyncOperation<Void> submit(String messageType, Consumer<Message> consumer, String queue, long delay, boolean traceRequired) {
        return submit(messageType, consumer, queue, delay, traceRequired, null);
    }

    @Override
    public AsyncOperation<Void> submit(String messageType, Consumer<Message> consumer, String queue, long delay, boolean traceRequired, Map<String, Object> attributes) {
        var taskId = UUID.randomUUID().toString();
        final FeedbackAsyncOperation feedback;
        if (traceRequired) {
            if (attributes == null) {
                attributes = new HashMap<>();
            } else {
                attributes = new HashMap<>(attributes);
            }

            attributes.put("type", messageType);
            feedback = registerTask(taskId, attributes);
        } else {
            feedback = null;
        }

        var tenant = UserContextManager.getTenant();
        producer.submit(context -> {
            try {
                var message = context.createMessage();
                message.setJMSType(messageType);
                message.setStringProperty("taskId", taskId);
                message.setStringProperty("tenant", tenant);
                message.setBooleanProperty("registered", traceRequired);
                if (consumer != null) {
                    consumer.accept(message);
                }

                if (delay > 0) {
                    message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + delay);
                }
                return message;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }, Optional.ofNullable(queue).orElse(asyncConfig.operations().queue()));

        log.info("Async {} operation submitted with taskId {}", messageType, taskId);
        var result = new CompletableAsyncOperation<Void>(taskId);
        if (feedback != null) {
            result.setCreatedAt(feedback.getCreatedAt());
            result.setUpdatedAt(feedback.getUpdatedAt());
        }

        return result;
    }
}
