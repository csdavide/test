package it.doqui.libra.librabl.business.provider.integration.droid;

import lombok.extern.slf4j.Slf4j;
import uk.gov.nationalarchives.droid.container.AbstractIdentifierEngine;
import uk.gov.nationalarchives.droid.container.ContainerSignatureMatchCollection;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;

import java.io.IOException;

@Slf4j
public class MyZipIdentifierEngine extends AbstractIdentifierEngine {

    public MyZipIdentifierEngine() {
    }

    @Override
    public void process(IdentificationRequest request, ContainerSignatureMatchCollection matches) throws IOException {
        var reader = this.newByteReader(request.getSourceInputStream());
        for (var match : matches.getContainerSignatureMatches()) {
            match.matchBinaryContent(request.getFileName(), reader);
        }
    }

}
