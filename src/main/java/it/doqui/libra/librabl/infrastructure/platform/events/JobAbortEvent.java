package it.doqui.libra.librabl.infrastructure.platform.events;

import java.util.Optional;

public class JobAbortEvent extends DistributedEvent {

    public String getJobId() {
        return Optional.ofNullable(getData())
                .map(d -> d.get("jobId"))
                .map(Object::toString)
                .orElse(null);
    }

}
