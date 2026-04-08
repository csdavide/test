package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.async.FeedbackAsyncOperation;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Optional;

public interface AsyncOperationService {
    Optional<FeedbackAsyncOperation> getTask(String tenant, String taskId);
    AsyncOperation<?> performOperations(Collection<NodeOperation> operations, @NotNull OperationParameters params);
    void executeAsyncOperations(String taskId, Collection<NodeOperation> operations, OperationParameters params);
}
