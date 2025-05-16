package it.doqui.libra.librabl.views.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.views.node.ContentProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.*;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileFormatDescriptor {
    private ContentProperty contentProperty;
    private ZonedDateTime identifiedAt;
    private final Set<PDFAnalyzer> pdfAnalyzers;
    private final Map<FileProperty, Set<PDFAnalyzer>> fileProperties;
    private int signaturesNumber;
    private SignatureType signatureType;
    private FileType fileType;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<FileFormatItem> items;

    public FileFormatDescriptor() {
        this.items = new LinkedList<>();
        this.pdfAnalyzers = new HashSet<>(List.of(PDFAnalyzer.OPENPDF, PDFAnalyzer.PDFBOX, PDFAnalyzer.TIKA));
        this.fileProperties = new HashMap<>();
    }

    public enum Result {
        NEGATIVE,
        POSITIVE_GENERIC,
        POSITIVE_SPECIFIC,
        TENTATIVE
    }

    public enum FileProperty {
        PASSWORD_PROTECTED,
        DAMAGED,
        ENCRYPTED
    }

    public enum FormatIdentifier {
        DROID,
        TIKA,
        LIBRA
    }

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class FileFormatItem {
        private String puid;
        private final List<String> mimeTypes;
        private String name;
        private String formatVersion;
        private final List<String> extensions;
        private Boolean fileExtensionMismatch;
        private String matchMethod;
        private FormatIdentifier formatIdentifier;
        private Result result;

        public FileFormatItem() {
            this.mimeTypes = new ArrayList<>();
            this.extensions = new ArrayList<>();
        }
    }
}
