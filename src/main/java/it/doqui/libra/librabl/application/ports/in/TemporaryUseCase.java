package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.document.DocumentStream;
import it.doqui.libra.librabl.domain.model.files.ContentDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;

import java.io.InputStream;
import java.time.Duration;

public interface TemporaryUseCase {
    String getTemporaryTenant();
    ContentRef createEphemeralNode(ContentDescriptor descriptor, InputStream body, Duration duration);
    ContentRef createEphemeralNode(ContentDescriptor descriptor, InputNodeRequest extra, InputStream body, Duration duration);
    ContentRef createEphemeralNode(DocumentStream documentStream);
    ContentRef createEphemeralNode(DocumentStream documentStream, InputNodeRequest extra);
    void unephemeralize(String ephemeralUuid);
}
