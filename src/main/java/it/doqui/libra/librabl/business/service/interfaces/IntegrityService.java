package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.views.management.SystemStatusInfo;

public interface IntegrityService {
    SystemStatusInfo checkSystemStatus(int expectedInstances, long timeout) throws InterruptedException;
}
