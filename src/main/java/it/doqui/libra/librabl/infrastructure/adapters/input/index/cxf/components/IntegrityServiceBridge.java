package it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components;

import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.index.ecmengine.mtom.exception.EcmEngineException;
import it.doqui.libra.librabl.application.ports.in.IntegrityUseCase;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.rmi.RemoteException;

@IfBuildProperty(name = "libra.module.cxf.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class IntegrityServiceBridge extends AbstractServiceBridge {

    @Inject
    IntegrityUseCase integrityUseCase;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public boolean testResources() throws EcmEngineException, RemoteException {
        try {
            return integrityUseCase.checkSystemStatus(1, 10000).isOk();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EcmEngineException(e.getMessage());
        }
    }
}
