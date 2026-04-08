package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.exceptions.AnalysisException;
import it.doqui.libra.librabl.application.model.document.FileFormatDescriptor;
import it.doqui.libra.librabl.application.model.document.FileCharacteristics;
import it.doqui.libra.librabl.domain.model.files.ContentRef;

import java.io.File;
import java.io.InputStream;

public interface DocumentAnalyzeUseCase {
    FileFormatDescriptor getFileFormat(ContentRef contentRef) throws AnalysisException;
    FileFormatDescriptor getFileFormat(File file, String fileName) throws AnalysisException;
    FileCharacteristics getSignatureType(InputStream stream);

}
