package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.service.EncryptionService;
import it.doqui.libra.librabl.domain.model.exceptions.DocumentOperationException;
import it.doqui.libra.librabl.application.ports.in.DocumentUseCase;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.SignJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@ApplicationScoped
@Slf4j
public class SignJobExecutor implements JobExecutor, ReservedJob {

    @Inject
    DocumentUseCase documentService;

    @Inject
    EncryptionService encryptionService;

    @Override
    public String getHandledKind() {
        return "sign";
    }

    @Override
    public boolean isLongOperationRequired() {
        return true;
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof SignJobRequest signJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        try {
            var sign = signJobRequest.getSign();
            var result = documentService.signDocument(sign.getDocumentRef(), sign.getSignParams(), sign.getStoreParams());
            result.setKind(request.getKind());
            return result;
        } catch (IOException | DocumentOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encryptRequest(JobRequest request) {
        if (!(request instanceof SignJobRequest signJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var signParams = signJobRequest.getSign().getSignParams();
        signParams.setPassword(encryptionService.encryptText(signParams.getPassword()));
        signParams.setPin(encryptionService.encryptText(signParams.getPin()));
        signParams.setOtp(encryptionService.encryptText(signParams.getOtp()));
        signParams.setTsPassword(encryptionService.encryptText(signParams.getTsPassword()));
    }

    @Override
    public void decryptRequest(JobRequest request) {
        if (!(request instanceof SignJobRequest signJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var signParams = signJobRequest.getSign().getSignParams();
        signParams.setPassword(encryptionService.decryptText(signParams.getPassword()));
        signParams.setPin(encryptionService.decryptText(signParams.getPin()));
        signParams.setOtp(encryptionService.decryptText(signParams.getOtp()));
        signParams.setTsPassword(encryptionService.decryptText(signParams.getTsPassword()));
    }
}
