package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.exception.EcmEngineException;
import it.doqui.libra.librabl.business.service.interfaces.IntegrityService;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.rmi.RemoteException;

@ApplicationScoped
@Slf4j
public class IntegrityServiceBridge extends AbstractServiceBridge {

    @Inject
    IntegrityService integrityService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public boolean testResources() throws EcmEngineException, RemoteException {
        try {
            return integrityService.checkSystemStatus(1, 10000).isOk();
        } catch (InterruptedException e) {
            throw new EcmEngineException(e.getMessage());
        }
    }
}
