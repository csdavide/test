package it.doqui.libra.librabl.business.provider.engine;

import com.lowagie.text.exceptions.BadPasswordException;
import com.lowagie.text.pdf.PdfReader;
import io.quarkus.tika.TikaContent;
import io.quarkus.tika.TikaParser;
import it.doqui.libra.librabl.business.provider.integration.droid.DroidAnalyzer;
import it.doqui.libra.librabl.business.service.exceptions.AnalysisException;
import it.doqui.libra.librabl.business.service.interfaces.DocumentAnalyzer;
import it.doqui.libra.librabl.business.service.interfaces.NodeContentService;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.document.*;
import it.doqui.libra.librabl.views.node.ContentRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.tsp.cms.CMSTimeStampedDataParser;
import org.dom4j.io.SAXReader;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;
import static it.doqui.libra.librabl.views.document.PDFAnalyzer.*;

@ApplicationScoped
@Slf4j
public class DocumentAnalyzerImpl implements DocumentAnalyzer {

    @Inject
    DroidAnalyzer droidAnalyzer;

    @Inject
    NodeContentService nodeContentService;

    @Inject
    TikaParser tikaParser;

    @ConfigProperty(name = "libra.formats.tika.timeout", defaultValue = "15s")
    Duration tikaTimeout;

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public FileFormatDescriptor getFileFormat(ContentRef contentRef) throws AnalysisException {
        var a = nodeContentService.getNodeContent(contentRef);
        return getFileFormat(a.getFile(), a.getName());
    }

    @Override
    public FileFormatDescriptor getFileFormat(File file, String fileName) throws AnalysisException {
        var ffd = droidAnalyzer.analyze(file, fileName);

        if (ffd.getItems().isEmpty()) {
            var ffi = new FileFormatDescriptor.FileFormatItem();
            ffi.setResult(FileFormatDescriptor.Result.NEGATIVE);
            ffi.setFormatIdentifier(FileFormatDescriptor.FormatIdentifier.DROID);
            ffd.getItems().add(ffi);
            log.debug("No results from Droid");
        }
        log.debug("Calling Tika to retrieve mimetype...");

        addTikaRecognizing(ffd, file, fileName);
        manageExtensions(ffd.getItems(), fileName);

        var extensions = ffd.getItems().stream().flatMap(x -> x.getExtensions().stream()).toList();
        var mimetypes = ffd.getItems().stream().flatMap(x -> x.getMimeTypes().stream()).toList();
        var isPdf = isPdfFile(extensions, mimetypes, fileName);

        try {
            var fileCharacteristics = getFileAndSignatureType(new FileInputStream(file), isPdf);
            fillFileFormatProperties(ffd, fileCharacteristics);
        } catch (FileNotFoundException e) {
            throw new AnalysisException(e);
        }

        addLibraRecognizing(ffd, file);
        return ffd;
    }

    @Override
    public FileCharacteristics getSignatureType(InputStream stream) {
        try {
            return getFileAndSignatureType(stream, null);
        } catch (AnalysisException e) {
            throw new SystemException(e);
        }
    }

    private FileCharacteristics getFileAndSignatureType(InputStream stream, Boolean isPdf) throws AnalysisException {
        var result = new FileCharacteristics();
        try {
            var data = IOUtils.readFully(stream);
            try (var bais = new ByteArrayInputStream(data)) {
                var reader = new SAXReader();
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                org.dom4j.Document doc = reader.read(bais);
                var signature = doc.getRootElement().element("Signature");
                if (signature != null && StringUtils.equals(signature.getNamespaceURI(), "http://www.w3.org/2000/09/xmldsig#")) {
                    return new FileCharacteristics(FileType.XML, SignatureType.XADES);
                } else {
                    return new FileCharacteristics(FileType.XML, SignatureType.UNSIGNED);
                }
            } catch (Exception ignored) {
            }

            try {
                new CMSSignedData(data);
                return new FileCharacteristics(FileType.P7M, SignatureType.CADES);
            } catch (Exception ignored) {
            }

            try {
                new CMSTimeStampedDataParser(data);
                return new FileCharacteristics(FileType.TIMESTAMPED, SignatureType.TIMESTAMP);
            } catch (Exception ignored) {
            }

            analyzeWithOpenPdf(data, isPdf, result);
            analyzeWithPdfBox(data, isPdf, result);
            analyzeWithTika(data, isPdf, result);
            log.debug("PDF analysis results: openPDF: {}; pdfBox: {}; tika: {}", result.get(OPENPDF), result.get(PDFBOX), result.get(TIKA));
            setFileSignatureType(result);

            if (isPdf == null) {
                if (result.getFileType() != null && result.getSignatureType() != null) {
                    return new FileCharacteristics(result.getFileType(), result.getSignatureType());
                } else {
                    return new FileCharacteristics(FileType.UNKNOWN, SignatureType.UNKNOWN);
                }
            }
            return result;
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    private void analyzeWithOpenPdf(byte[] data, Boolean isPdf, FileCharacteristics result) {
        try (var reader = new PdfReader(data)) {
            if (reader.isEncrypted()) {
                ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.ENCRYPTED, OPENPDF);
            }
            if (reader.getLastXref() == 0) {
                ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.DAMAGED, OPENPDF);
            }

            final var acroFields = reader.getAcroFields();
            if (acroFields != null) {
                final var signatureNames = acroFields.getSignedFieldNames();
                int signatureCount = 0;
                if (signatureNames != null && !signatureNames.isEmpty()) {
                    for (String signatureName : signatureNames) {
                        try {
                            var pkcs7 = acroFields.verifySignature(signatureName);
                            if (pkcs7.verify()) {
                                signatureCount++;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                ObjectUtils.addValueInSet(result.getNumberOfSignatures(), signatureCount, OPENPDF);
            } else {
                log.debug("{}: No acro fields found", OPENPDF.name());
            }
        } catch (Exception e) {
            if (e instanceof BadPasswordException) {
                ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.PASSWORD_PROTECTED, OPENPDF);
            } else if (isPdf != null && isPdf) {
                ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.DAMAGED, OPENPDF);
            }
        }
    }

    private void analyzeWithPdfBox(byte[] data, Boolean isPdf, FileCharacteristics result) {
        try {
            final var parser = new PDFParser(new RandomAccessBuffer(data));
            parser.setLenient(true);
            parser.parse();
            try (final var pdDocument = parser.getPDDocument()) {
                if (pdDocument.isEncrypted() || pdDocument.getDocument().isEncrypted()) {
                    ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.ENCRYPTED, PDFBOX);
                }
                if (pdDocument.getDocument().getStartXref() == 0) {
                    ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.DAMAGED, PDFBOX);
                }
                final var acroForm = pdDocument.getDocumentCatalog().getAcroForm();
                if (acroForm != null) {
                    final var fields = acroForm.getFields();
                    int signatureCount = 0;
                    for (PDField field : fields) {
                        if (field instanceof PDSignatureField) {
                            signatureCount++;
                        }
                    }
                    ObjectUtils.addValueInSet(result.getNumberOfSignatures(), signatureCount, PDFBOX);
                } else {
                    log.debug("{}: No acro fields found", PDFBOX.name());
                }
            }
        } catch (Exception e) {
            if (e instanceof InvalidPasswordException) {
                ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.PASSWORD_PROTECTED, PDFBOX);
            } else if (isPdf != null && isPdf) {
                ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.DAMAGED, PDFBOX);
            }
        }
    }

    private void analyzeWithTika(byte[] data, Boolean isPdf, FileCharacteristics result) {
        try (var is = new ByteArrayInputStream(data)) {
            try {
                Callable<TikaContent> task = () -> tikaParser.parse(is);
                var f = executorService.submit(task);
                f.get(tikaTimeout.toSeconds(), TimeUnit.SECONDS);
                log.debug("Content parsed correctly.");
            } catch (ExecutionException e) {
                if (isPdf != null && isPdf) {
                    if (ObjectUtils.isCauseBy(e, InvalidPasswordException.class) || ObjectUtils.isCauseBy(e, EncryptedDocumentException.class)) {
                        ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.PASSWORD_PROTECTED, TIKA);
                    } else {
                        ObjectUtils.addValueInSet(result.getPdfProperties(), FileFormatDescriptor.FileProperty.DAMAGED, TIKA);
                    }
                }
            } catch (TimeoutException e) {
                log.warn("Tika timeout");
            } catch (InterruptedException e) {
                log.debug("{} Interrupted: {}", TIKA.name(), e.getMessage());
            }
        } catch (LinkageError e) {
            log.error("{}", e.getMessage());
        } catch (IOException ignored) {
        }
    }

    private void manageExtensions(List<FileFormatDescriptor.FileFormatItem> items, String fileName) {
        if (items != null && !items.isEmpty() && fileName != null) {
            LinkedList<String> extensions = new LinkedList<>();
            items.forEach(ffi -> {
                if (ffi.getExtensions() != null && ffi.getExtensions().size() > 1) {
                    var matchingExtension = ffi.getExtensions().stream().filter(ext -> StringUtils.equals(ext, fileName.substring(fileName.lastIndexOf('.') + 1))).findFirst().orElse(null);
                    var otherExtensions = ffi.getExtensions().stream().filter(ext -> !StringUtils.equals(ext, fileName.substring(fileName.lastIndexOf('.') + 1))).toList();
                    extensions.addAll(otherExtensions);
                    if (matchingExtension != null) {
                        extensions.addFirst(matchingExtension);
                    }
                    ffi.getExtensions().clear();
                    ffi.getExtensions().addAll(extensions);
                }
            });
        }
    }

    private void fillFileFormatProperties(FileFormatDescriptor ffd, FileCharacteristics fileCharacteristics) {
        ffd.setSignatureType(fileCharacteristics.getSignatureType());
        ffd.setFileType(fileCharacteristics.getFileType());
        ffd.getFileProperties().putAll(fileCharacteristics.getPdfProperties());
        if (!fileCharacteristics.getNumberOfSignatures().isEmpty()) {
            ffd.setSignaturesNumber(Collections.max(fileCharacteristics.getNumberOfSignatures().keySet()));
        }
    }

    private boolean isPdfFile(List<String> extensions, List<String> mimetypes, String fileName) {
        if (extensions == null || mimetypes == null) {
            return false;
        }
        if (extensions.contains("pdf") || mimetypes.contains("application/pdf")) {
            return true;
        }
        return fileName != null && fileName.endsWith(".pdf");
    }

    private void setFileSignatureType(FileCharacteristics result) {
        if (result.isPresent(TIKA, FileFormatDescriptor.FileProperty.DAMAGED)) {
            result.setFileType(FileType.UNKNOWN);
            result.setSignatureType(SignatureType.UNKNOWN);
            return;
        }
        if (result.isPresent(FileFormatDescriptor.FileProperty.PASSWORD_PROTECTED)) {
            result.setFileType(FileType.PDF_PROTECTED);
            result.setSignatureType(SignatureType.UNKNOWN);
            return;
        }
        if (result.isPresent(FileFormatDescriptor.FileProperty.DAMAGED)) {
            result.setFileType(FileType.UNKNOWN);
            result.setSignatureType(SignatureType.UNKNOWN);
            return;
        }
        if (result.isPresent(FileFormatDescriptor.FileProperty.ENCRYPTED)) {
            result.setFileType(FileType.PDF_PROTECTED);
        }
        if (!result.getNumberOfSignatures().isEmpty()) {
            if (result.getFileType() == null) {
                result.setFileType(FileType.PDF);
            }
            if (Collections.max(result.getNumberOfSignatures().keySet()) > 0) {
                result.setSignatureType(SignatureType.PADES);
            } else {
                result.setSignatureType(SignatureType.UNSIGNED);
            }
        } else {
            result.setFileType(FileType.UNKNOWN);
            result.setSignatureType(SignatureType.UNKNOWN);
        }
    }

//    private Integer signCount(Integer signCountItext, Integer signCountPdfBox, Integer signCountTika) {
//        if (signCountTika == null && signCountPdfBox == null && signCountItext == null) {
//            return null;
//        } else if (signCountTika != null && signCountTika == -2) {
//            return -2;
//        } else if (signCountItext != null && signCountItext == -1 || signCountPdfBox != null && signCountPdfBox == -1 || signCountTika != null && signCountTika == -1) {
//            return -1;
//        } else {
//            var nonNullResults = Stream.of(signCountItext, signCountPdfBox, signCountTika).filter(Objects::nonNull).toList();
//            return nonNullResults.stream().mapToInt(x -> x).max().orElseThrow();
//        }
//    }

    private void addTikaRecognizing(FileFormatDescriptor ffd, File file, String fileName) throws AnalysisException {
        try (var fis = new FileInputStream(file)) {
            var mimetypes = MimeTypes.getDefaultMimeTypes();

            var tikaStream = TikaInputStream.get(fis);
            var detector = new Tika().getDetector();
            var metadata = new Metadata();
            var filename = Optional.ofNullable(fileName).orElse(file.getName());

            metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            var mimetype = detector.detect(tikaStream, metadata).toString();
            log.debug("Mimetype found: {}", mimetype);

            var extensions = mimetypes.forName(mimetype).getExtensions();
            if (extensions.isEmpty()) {
                extensions = mimetypes.forName(mimetype.split(";")[0]).getExtensions();
            }
            log.debug("Extensions found: {}", extensions);

            var ffi = new FileFormatDescriptor.FileFormatItem();
            ffi.setFormatIdentifier(FileFormatDescriptor.FormatIdentifier.TIKA);
            if (StringUtils.isNotBlank(mimetype)) {
                ffi.getMimeTypes().addAll(Arrays.stream(mimetype.split(", ")).toList());
                ffi.setResult(FileFormatDescriptor.Result.POSITIVE_GENERIC);
                ffi.getExtensions().addAll(extensions.stream().map(ext -> StringUtils.strip(ext, ".")).toList());
            } else {
                ffi.setResult(FileFormatDescriptor.Result.NEGATIVE);
            }
            ffd.getItems().add(ffi);
        } catch (IOException | MimeTypeException e) {
            throw new AnalysisException(e.getMessage());
        }
    }

    private void addLibraRecognizing(FileFormatDescriptor ffd, File file) {
        var tikaMimetypes = ffd.getItems().stream()
            .filter(ffi -> Objects.equals(ffi.getFormatIdentifier(), FileFormatDescriptor.FormatIdentifier.TIKA))
            .flatMap(ffi -> ffi.getMimeTypes().stream())
            .toList();

        var droidPuids = ffd.getItems().stream()
            .filter(ffi -> Objects.equals(ffi.getFormatIdentifier(), FileFormatDescriptor.FormatIdentifier.DROID))
            .map(FileFormatDescriptor.FileFormatItem::getPuid)
            .toList();

        var droidMimetypes = ffd.getItems().stream()
            .filter(ffi -> Objects.equals(ffi.getFormatIdentifier(), FileFormatDescriptor.FormatIdentifier.DROID))
            .flatMap(ffi -> ffi.getMimeTypes().stream())
            .toList();

        if (tikaMimetypes.size() == 1
            && tikaMimetypes.contains(MIMETYPE_TEXT_PLAIN) && (Objects.equals(ffd.getFileType(), FileType.XML) || droidMimetypes.contains(MIMETYPE_APPLICATION_XML) || droidMimetypes.contains(MIMETYPE_TEXT_XML))) {
            log.debug("Adding LIBRA recognizing...");
            ffd.getItems().add(createLibraRecognizingItem(MIMETYPE_APPLICATION_XML, "xml"));
        }

        try (var workbook = WorkbookFactory.create(file)) {
            workbook.isHidden();
        } catch (EncryptedDocumentException e) {
            if (droidPuids.size() == 1 && droidPuids.contains("fmt/494") && droidMimetypes.isEmpty()) {
                log.debug("Adding LIBRA recognizing...");
                ffd.getItems().add(createLibraRecognizingItem(MIMETYPE_XLSX_PROTECTED, "xlsx"));
            }
        } catch (Exception e) {
            if (e.getCause() == null) {
                log.error("{}: {}", e.getClass(), e.getMessage());
            } else {
                log.error("{}: {}: {}", e.getClass(), e.getMessage(), e.getCause().getMessage());
            }
        }

        //TODO: mancano gli xls protetti, poi doc/docx, ppt/pptx; poi i rar [vedere issue 21]
    }

    private FileFormatDescriptor.FileFormatItem createLibraRecognizingItem(String mimetype, String extension) {
        var ffi = new FileFormatDescriptor.FileFormatItem();
        ffi.setFormatIdentifier(FileFormatDescriptor.FormatIdentifier.LIBRA);
        ffi.getMimeTypes().add(mimetype);
        ffi.setResult(FileFormatDescriptor.Result.POSITIVE_GENERIC);
        ffi.getExtensions().add(extension);
        return ffi;
    }
}
