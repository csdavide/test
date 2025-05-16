package it.doqui.libra.librabl.views.document;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
public class FileCharacteristics {

    private FileType fileType;
    private SignatureType signatureType;
    private final Map<Integer, Set<PDFAnalyzer>> numberOfSignatures;
    private final Map<FileFormatDescriptor.FileProperty, Set<PDFAnalyzer>> pdfProperties;

    public FileCharacteristics(FileType fileType, SignatureType signatureType) {
        this.fileType = fileType;
        this.signatureType = signatureType;
        this.numberOfSignatures = new HashMap<>();
        this.pdfProperties = new HashMap<>();
    }

    public FileCharacteristics() {
        this.numberOfSignatures = new HashMap<>();
        this.pdfProperties = new HashMap<>();
    }

    public String get(PDFAnalyzer analyzer) {
        var result = new StringBuilder();
        for (var entry : this.pdfProperties.entrySet()) {
            if (entry.getValue().contains(analyzer)) {
                if (!result.isEmpty()) {
                    result.append(", ");
                }
                result.append(entry.getKey());
            }
        }
        for (var entry : this.numberOfSignatures.entrySet()) {
            if (entry.getValue().contains(analyzer)) {
                if (!result.isEmpty()) {
                    result.append(", ");
                }
                result.append(entry.getKey());
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        return result.toString();
    }

    public boolean isPresent(PDFAnalyzer analyzer, FileFormatDescriptor.FileProperty fileProperty) {
        return this.getPdfProperties().get(fileProperty) != null && this.getPdfProperties().get(fileProperty).contains(analyzer);
    }

    public boolean isPresent(FileFormatDescriptor.FileProperty fileProperty) {
        return this.getPdfProperties().containsKey(fileProperty) && !this.getPdfProperties().get(fileProperty).isEmpty();
    }
}
