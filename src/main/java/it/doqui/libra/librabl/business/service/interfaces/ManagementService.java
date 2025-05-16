package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.views.management.MgmtOperation;
import it.doqui.libra.librabl.views.management.VolumeInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ManagementService {
    Map<?,?> getBootAttributes();

    Collection<VolumeInfo> getVolumes();
    AsyncOperation<Void> submitNodeReindex(String tenant, String node, boolean recursive, int priority, int blockSize);
    AsyncOperation<Void> submitTransactionsReindex(String tenant, List<Long> transactions, int priority);
    AsyncOperation<Void> submitVolumesCalculation();
    AsyncOperation<Collection<VolumeInfo>> getCalculatedVolumes(String taskId);
    void deleteCalculatedVolumes(String taskId);
    void performOperations(List<MgmtOperation> operations);
    void performOperations(String tenant, List<MgmtOperation> operations);
    Optional<FeedbackAsyncOperation> getTask(String tenant, String taskId);
}
