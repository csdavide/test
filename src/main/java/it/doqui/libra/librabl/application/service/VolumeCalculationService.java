package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.model.async.CompletableAsyncOperation;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.application.ports.in.VolumeCalculationUseCase;
import it.doqui.libra.librabl.application.ports.out.JobRepository;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.application.model.jobs.requests.VolumeCalculationJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CalculatedVolumesResult;
import it.doqui.libra.librabl.application.model.management.VolumeInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@ApplicationScoped
@Slf4j
public class VolumeCalculationService implements VolumeCalculationUseCase {

    @Inject
    AuthenticationManagerPort authenticationService;

    @Inject
    JobRepository jobRepository;

    @Inject
    JobUseCase jobService;

    @Override
    public AsyncOperation<Void> submitVolumesCalculation() {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
        var jobRequest = new VolumeCalculationJobRequest();
        jobRequest.setMode(OperationMode.ASYNC);
        return new CompletableAsyncOperation<>(jobService.executeJob(jobRequest));
    }

    @Override
    public AsyncOperation<Collection<VolumeInfo>> getCalculatedVolumes(String taskId) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
        var jobResponse = jobRepository.getJob(taskId);
        var response = new CompletableAsyncOperation<Collection<VolumeInfo>>(jobResponse);
        if (jobResponse.isCompleted() && jobResponse.getResult() instanceof CalculatedVolumesResult result) {
            response.complete(result.getVolumes());
        }

        return response;
    }

    @Override
    public void deleteCalculatedVolumes(String taskId) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
        jobRepository.deleteJob(taskId, r -> r.getResult() instanceof CalculatedVolumesResult);
    }
}
