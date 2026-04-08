package it.doqui.libra.librabl.infrastructure.adapters.input.index.rest.components.impl;

import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.libra.librabl.application.ports.in.ContentUseCase;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components.ContentServiceBridge;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components.DocumentServiceBridge;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.rest.dto.EncryptionInfo;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.rest.dto.FileFormatInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

@IfBuildProperty(name = "libra.module.cxf.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class ContentBusinessComponent extends AbstractBusinessComponent implements BusinessComponent {

    @Inject
    ContentUseCase contentUseCase;

    @Inject
    DocumentServiceBridge documentService;

    @Inject
    ContentServiceBridge contentService;

    @Override
    public Class<?> getComponentInterface() {
        return NodesBusinessInterface.class;
    }

    public File retrieveContentData(String uid, String contentPropertyName) {
        try {
            var attachment = contentUseCase.getNodeContent(new ContentRef().setUuid(uid).setContentPropertyName(contentPropertyName));
            return attachment.getFile();
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    public void updateContentData(String uid, String contentPropertyName, String mimeType, String encoding, byte[] bytes) {
        var cs = new ContentStream();
        cs.setName(contentPropertyName);
        cs.setMimetype(mimeType);
        cs.setEncoding(encoding);
        cs.setInputStream(new ByteArrayInputStream(bytes));
        contentUseCase.setNodeContent(uid, cs, null);
    }

    public String extractFromEnvelope(String uid, String contentPropertyName) {
        var document = document(uid, contentPropertyName);
        var result = documentService.extractDocumentFromEnvelopeEnvelopedDocument(document, null);
        return result.getUid();
    }

    public FileFormatInfo[] identifyDocument(String uid, String contentPropertyName, Boolean store) {
        var document = document(uid, contentPropertyName);
        document.getOperation().setTempStore(BooleanUtils.toBoolean(store));
        return map(contentService.identifyDocument(document, null));
    }

    public String generateDigestFromUID(String uid, String contentPropertyName, String algorithm, Boolean enveloped) {
        var nodeInfo = new NodeInfo();
        nodeInfo.setEnveloped(enveloped);
        nodeInfo.setPrefixedName(contentPropertyName);

        var digestInfo = new DigestInfo();
        digestInfo.setAlgorithm(algorithm);

        var result = documentService.generateDigestFromUID(new Node(uid), nodeInfo, digestInfo, null);
        return result.getDigest();
    }

    public String getSignatureType(String uid, String contentPropertyName, EncryptionInfo encryptionInfo) {
        var content = new Content();
        content.setContentPropertyPrefixedName(contentPropertyName);
        content.setEncryptionInfo(dtoMapper.convert(encryptionInfo, it.doqui.index.ecmengine.mtom.dto.EncryptionInfo.class));

        return documentService.getSignatureType(new Node(uid), content, null);
    }

    private Document document(String uid, String contentPropertyName) {
        var document = new Document();
        document.setUid(uid);
        document.setContentPropertyPrefixedName(contentPropertyName);
        var operation = new DocumentOperation();
        operation.setReturnData(false);
        operation.setTempStore(true);
        document.setOperation(operation);
        return document;
    }
}
