package it.doqui.libra.librabl.domain.model.jobs;

import java.util.concurrent.atomic.AtomicBoolean;

public interface JobContext {
    String getJobId();
    AtomicBoolean getCancelled();
}
