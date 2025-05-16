package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.views.node.ContentRef;
import it.doqui.libra.librabl.views.node.ContentRequest;
import it.doqui.libra.librabl.views.node.ContentStream;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface NodeContentService {
    NodeAttachment getNodeContent(ContentRef contentRef);
    NodeAttachment getNodeContent(String uuid, String contentPropertyName) throws IOException;
    NodeAttachment getNodeContent(String uuid, String contentPropertyName, String fileName) throws IOException;
    Map<String,NodeAttachment> getNodeContents(Collection<ContentRequest> uuids, Long limit) throws IOException;
    void setNodeContent(String uuid, ContentStream cs, String currentFilename);
    void addNodeContent(String uuid, ContentStream cs);
    void removeNodeContent(String uuid, String contentPropertyName, String fileName);
}
