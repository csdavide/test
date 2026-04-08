package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;

public interface ReservedJob {
    void encryptRequest(JobRequest request);
    void decryptRequest(JobRequest request);
}
