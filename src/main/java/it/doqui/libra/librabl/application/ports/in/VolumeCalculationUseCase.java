package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.application.model.management.VolumeInfo;

import java.util.Collection;

public interface VolumeCalculationUseCase {
    AsyncOperation<Void> submitVolumesCalculation();
    AsyncOperation<Collection<VolumeInfo>> getCalculatedVolumes(String taskId);
    void deleteCalculatedVolumes(String taskId);
}
