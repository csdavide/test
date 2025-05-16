package it.doqui.libra.librabl.business.service.async;

import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.views.AbstractOperation;
import jakarta.jms.Message;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface AsyncOperationService {
    Optional<FeedbackAsyncOperation> getTask(String taskId);
    FeedbackAsyncOperation registerTask(String taskId, Map<String, Object> attributes);
    void removeTask(String taskId, Function<FeedbackAsyncOperation, Boolean> filter);
    void removeTask(String taskId);
    void completeTask(String taskId, AsyncOperation.Status status, Map<String, Object> attributes);
    AsyncOperation<Void> submit(String messageType, String uuid, Collection<? extends AbstractOperation<?>> operations, long delay);
    AsyncOperation<Void> submit(String messageType, Consumer<Message> consumer, String queue, long delay, boolean traceRequired);
    AsyncOperation<Void> submit(String messageType, Consumer<Message> consumer, String queue, long delay, boolean traceRequired, Map<String, Object> attributes);
}
