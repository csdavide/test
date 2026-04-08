package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.application.model.user.PkItem;
import it.doqui.libra.librabl.application.model.share.KeyRequest;
import it.doqui.libra.librabl.application.model.share.SharingItem;
import it.doqui.libra.librabl.application.model.share.SharingRequest;

import java.util.Collection;

public interface SharedLinkUseCase {
    Collection<PkItem> listPublicKeys();
    NodeAttachment streamSharedContentData(String requestUrl, String inputKey);
    NodeAttachment streamSharedContentData(KeyRequest request);
    String shareNodeContent(String uuid, SharingRequest sharingRequest);
    void updateSharedLink(String uuid, String key, SharingRequest sharingRequest);
    void removeSharedLink(String uuid, String key);
    void removeAllSharedLinks(String uuid);
    Collection<SharingItem> listSharingItems(String uuid);
}
