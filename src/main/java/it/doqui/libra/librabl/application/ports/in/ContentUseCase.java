package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.application.model.graph.ContentRequest;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.graph.Vertex;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

public interface ContentUseCase {
    NodeAttachment getNodeContent(ContentRef contentRef) throws IOException;
    NodeAttachment getNodeContent(Vertex vertex, URI contentUrl) throws IOException;
    NodeAttachment getNodeContent(Vertex vertex, ContentReferenceable ref) throws IOException;
    Map<String,NodeAttachment> getNodeContents(Collection<ContentRequest> uuids, Long limit) throws IOException;
    void setNodeContent(String uuid, ContentStream cs, String currentFilename);
    void addNodeContent(String uuid, ContentStream cs);
    void removeNodeContent(String uuid, String contentPropertyName, String fileName);
}
