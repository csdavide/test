package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.share.KeyRequest;
import it.doqui.libra.librabl.views.share.SharingItem;
import it.doqui.libra.librabl.views.share.SharingRequest;

import java.util.Collection;

public interface SharedLinkService {
    Collection<PkItem> listPublicKeys();
    NodeAttachment streamSharedContentData(String requestUrl, String inputKey);
    NodeAttachment streamSharedContentData(KeyRequest request);
    String shareNodeContent(String uuid, SharingRequest sharingRequest);
    void updateSharedLink(String uuid, String key, SharingRequest sharingRequest);
    void removeSharedLink(String uuid, String key);
    void removeAllSharedLinks(String uuid);
    Collection<SharingItem> listSharingItems(String uuid);
}
