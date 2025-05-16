package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.AclListParams;
import it.doqui.libra.librabl.api.v1.cxf.impl.AclServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.AclRecord;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Arrays;

@ApplicationScoped
@Slf4j
public class AclBusinessComponent implements BusinessComponent {

    @Inject
    AclServiceBridge aclService;

    @Inject
    DtoMapper dtoMapper;

    @Override
    public Class<?> getComponentInterface() {
        return NodesBusinessInterface.class;
    }

    public void addAcl(String uid, AclRecord[] aclRecords) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        aclService.addAcl(id, map(aclRecords), null);
    }

    public void updateAcl(String uid, AclRecord[] aclRecords) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        aclService.updateAcl(id, map(aclRecords), null);
    }

    public void changeAcl(String uid, AclRecord[] aclRecords) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        aclService.changeAcl(id, map(aclRecords), null);
    }

    public void removeAcl(String uid, AclRecord[] aclRecords) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        aclService.removeAcl(id, map(aclRecords), null);
    }

    public void resetAcl(String uid, AclRecord filter) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var f = dtoMapper.convert(filter, it.doqui.index.ecmengine.mtom.dto.AclRecord.class);
        aclService.resetAcl(id, f, null);
    }

    public AclRecord[] listAcl(String uid, Boolean showInherited) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var params = new AclListParams();
        params.setShowInherited(BooleanUtils.isTrue(showInherited));
        return map(aclService.listAcl(id, params, null));
    }

    public boolean isInheritsAcl(String uid) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        return aclService.isInheritsAcl(id, null);
    }

    public void setInheritsAcl(String uid, boolean inheritance) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        aclService.setInheritsAcl(id, inheritance, null);
    }

    private it.doqui.index.ecmengine.mtom.dto.AclRecord[] map(AclRecord[] aclRecords) {
        return Arrays.stream(aclRecords)
            .map(acl -> dtoMapper.convert(acl, it.doqui.index.ecmengine.mtom.dto.AclRecord.class))
            .toList()
            .toArray(new it.doqui.index.ecmengine.mtom.dto.AclRecord[0]);
    }

    private AclRecord[] map(it.doqui.index.ecmengine.mtom.dto.AclRecord[] aclRecords) {
        return Arrays.stream(aclRecords)
            .map(acl -> dtoMapper.convert(acl, AclRecord.class))
            .toList()
            .toArray(new AclRecord[0]);
    }
}
