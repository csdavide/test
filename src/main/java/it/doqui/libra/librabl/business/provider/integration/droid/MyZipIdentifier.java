package it.doqui.libra.librabl.business.provider.integration.droid;

import uk.gov.nationalarchives.droid.container.AbstractContainerIdentifier;
import uk.gov.nationalarchives.droid.container.ContainerSignatureMatchCollection;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;

import java.io.IOException;

public class MyZipIdentifier extends AbstractContainerIdentifier {

    public MyZipIdentifier() {
        this.setIdentifierEngine(new MyZipIdentifierEngine());
    }

    @Override
    protected void process(IdentificationRequest request, ContainerSignatureMatchCollection matches) throws IOException {
        this.getIdentifierEngine().process(request, matches);
    }
}
