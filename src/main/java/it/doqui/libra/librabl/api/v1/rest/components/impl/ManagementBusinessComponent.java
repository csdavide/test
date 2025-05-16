package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.ModelDescriptor;
import it.doqui.libra.librabl.api.v1.cxf.impl.BackOfficeServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.SharedLinksServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.TenantServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.TenantsBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.CustomModel;
import it.doqui.libra.librabl.api.v1.rest.dto.ModelMetadata;
import it.doqui.libra.librabl.api.v1.rest.dto.Tenant;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ManagementBusinessComponent implements BusinessComponent {

    @Inject
    TenantServiceBridge tenantService;

    @Inject
    BackOfficeServiceBridge backOfficeService;

    @Inject
    SharedLinksServiceBridge sharedLinksService;

    @Inject
    DtoMapper dtoMapper;

    @Override
    public Class<?> getComponentInterface() {
        return TenantsBusinessInterface.class;
    }

    public Tenant[] listAllTenantNames() {
        var tenants = tenantService.getAllTenantNames(null);
        var result = new Tenant[tenants.length];
        for (int i = 0; i < tenants.length; i++) {
            result[i] = new Tenant();
            result[i].setName(tenants[i].getDomain());
        }

        return result;
    }

    public CustomModel[] getAllCustomModels() {
        var models = backOfficeService.getAllCustomModels(null);
        var result = new CustomModel[models.length];
        for (int i = 0; i < models.length; i++) {
            result[i] = dtoMapper.convert(models[i], CustomModel.class);
        }

        return result;
    }

    public ModelMetadata getModelDefinition(String prefixedName) {
        var md = new ModelDescriptor();
        md.setPrefixedName(prefixedName);
        var mm = backOfficeService.getModelDefinition(md, null);
        return dtoMapper.convert(mm, ModelMetadata.class);
    }

    public String[] getPublicKeys() {
        return sharedLinksService.getPublicKeys(null);
    }

    public void addPublicKey(String publicKey) {
        sharedLinksService.addPublicKey(publicKey, null);
    }

    public void removePublicKey(String publicKey) {
        sharedLinksService.removePublicKey(publicKey, null);
    }
}
