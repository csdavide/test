package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.application.model.management.SystemStatusInfo;

public interface IntegrityUseCase {
    SystemStatusInfo checkSystemStatus(int expectedInstances, long timeout) throws InterruptedException;
}
