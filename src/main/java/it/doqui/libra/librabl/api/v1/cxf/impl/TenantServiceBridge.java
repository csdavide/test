package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.MtomOperationContext;
import it.doqui.index.ecmengine.mtom.dto.Repository;
import it.doqui.index.ecmengine.mtom.dto.Tenant;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.business.service.interfaces.TenantService;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Objects;

@ApplicationScoped
@Slf4j
public class TenantServiceBridge extends AbstractServiceBridge {

    @Inject
    TenantService tenantService;


    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    //TODO: il contentStore non viene fornito all'esterno perché potrebbe essere diverso da macchina a macchina
    public Tenant[] getAllTenants(MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        NoDataExtractedException, PermissionDeniedException, EcmEngineException {

        return call(() -> tenantService.findStartingWith(null)
            .stream()
            .map(r -> {
                Tenant t = new Tenant();
                t.setDomain(r.getName());
                t.setEnabled(r.isEnabled());
                return t;
            })
            .toList()
            .toArray(new Tenant[0]));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Tenant[] getAllTenantNames(MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        NoDataExtractedException, PermissionDeniedException, EcmEngineException {

        return Arrays.stream(getAllTenants(context)).map(in -> {
                var out = new Tenant();
                out.setDomain(in.getDomain());
                out.setEnabled(in.isEnabled());
                return out;
            })
            .toList()
            .toArray(new Tenant[0]);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public boolean tenantExists(Tenant tenant, MtomOperationContext context)
        throws InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineException {

        validate(() -> {
            Objects.requireNonNull(tenant, "Tenant must not be null");
            ObjectUtils.requireNotBlank(tenant.getDomain(), "Tenant domain must not be null");

        });

        return call(() -> tenantService.findByIdOptional(TenantRef.valueOf(tenant.getDomain()).toString()).isPresent());
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Repository[] getRepositories(MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException {
        Repository[] repositories = new Repository[2];
        repositories[0] = new Repository("primary");
        repositories[1] = new Repository("secondary");
        return repositories;
    }
}
