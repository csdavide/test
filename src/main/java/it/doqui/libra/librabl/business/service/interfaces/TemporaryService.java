package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.document.DocumentStream;
import it.doqui.libra.librabl.views.node.ContentDescriptor;
import it.doqui.libra.librabl.views.node.ContentRef;
import it.doqui.libra.librabl.views.node.InputNodeRequest;

import java.io.InputStream;
import java.time.Duration;

public interface TemporaryService {
    String getTemporaryTenant();
    ContentRef createEphemeralNode(ContentDescriptor descriptor, InputStream body, Duration duration);
    ContentRef createEphemeralNode(ContentDescriptor descriptor, InputNodeRequest extra, InputStream body, Duration duration);
    ContentRef createEphemeralNode(DocumentStream documentStream);
    ContentRef createEphemeralNode(DocumentStream documentStream, InputNodeRequest extra);
    void unephemeralize(String ephemeralUuid);
}
