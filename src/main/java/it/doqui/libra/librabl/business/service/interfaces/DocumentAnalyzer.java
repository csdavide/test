package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.exceptions.AnalysisException;
import it.doqui.libra.librabl.views.document.FileFormatDescriptor;
import it.doqui.libra.librabl.views.document.FileCharacteristics;
import it.doqui.libra.librabl.views.node.ContentRef;

import java.io.File;
import java.io.InputStream;

public interface DocumentAnalyzer {
    FileFormatDescriptor getFileFormat(ContentRef contentRef) throws AnalysisException;
    FileFormatDescriptor getFileFormat(File file, String fileName) throws AnalysisException;
    FileCharacteristics getSignatureType(InputStream stream);

}
