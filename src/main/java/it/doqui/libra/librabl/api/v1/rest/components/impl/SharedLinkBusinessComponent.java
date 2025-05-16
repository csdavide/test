package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.Node;
import it.doqui.libra.librabl.api.v1.cxf.impl.SharedLinksServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.SharingInfo;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class SharedLinkBusinessComponent implements BusinessComponent {

    @Inject
    SharedLinksServiceBridge sharedLinksService;

    @Inject
    DtoMapper dtoMapper;

    @Override
    public Class<?> getComponentInterface() {
        return NodesBusinessInterface.class;
    }

    public SharingInfo[] getSharingInfos(String uid) {
        return map(sharedLinksService.getSharingInfos(new Node(uid), null));
    }

    public void retainDocument(String uid) {
        sharedLinksService.retainDocument(new Node(uid), null);
    }

    public void removeSharedLink(String uid, String shareLinkId) {
        var params = new it.doqui.index.ecmengine.mtom.dto.SharingInfo();
        params.setSharedLink(shareLinkId);
        sharedLinksService.removeSharedLink(new Node(uid), params, null);
    }

    public void updateSharedLink(String uid, String sharedLinkId, SharingInfo sharingInfo) {
        var params = dtoMapper.convert(sharingInfo, it.doqui.index.ecmengine.mtom.dto.SharingInfo.class);
        params.setSharedLink(sharedLinkId);
        sharedLinksService.updateSharedLink(new Node(uid), params, null);
    }

    public String shareDocument(String uid, SharingInfo sharingInfo) {
        var params = dtoMapper.convert(sharingInfo, it.doqui.index.ecmengine.mtom.dto.SharingInfo.class);
        return sharedLinksService.shareDocument(new Node(uid), params, null);
    }

    private SharingInfo[] map(it.doqui.index.ecmengine.mtom.dto.SharingInfo[] infos) {
        var result = new SharingInfo[infos.length];
        for (int i = 0; i < infos.length; i++) {
            result[i] = dtoMapper.convert(result[i], SharingInfo.class);
        }

        return result;
    }

}
