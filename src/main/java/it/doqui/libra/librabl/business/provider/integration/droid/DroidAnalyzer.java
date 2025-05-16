package it.doqui.libra.librabl.business.provider.integration.droid;

import it.doqui.libra.librabl.business.service.exceptions.AnalysisException;
import it.doqui.libra.librabl.views.document.FileFormatDescriptor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.archive.ContainerIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.FileSystemIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;
import uk.gov.nationalarchives.droid.internal.api.ApiResult;
import uk.gov.nationalarchives.droid.internal.api.ContainerApi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static it.doqui.libra.librabl.views.document.FileFormatDescriptor.Result.*;

@ApplicationScoped
@Slf4j
public class DroidAnalyzer {

    @ConfigProperty(name = "libra.formats.droid.signature.path", defaultValue = "./droid-signature.xml")
    String fileSignaturesFileName;

    @ConfigProperty(name = "libra.formats.droid.container.path", defaultValue = "./container-signature.xml")
    String containerSignaturesFileName;

    private DroidAPI api;

    @PostConstruct
    void init() throws Exception {
        this.api = DroidAPI.getInstance(
            Paths.get(fileSignaturesFileName),
            Paths.get(containerSignaturesFileName)
        );
    }

    public FileFormatDescriptor analyze(InputStream is, String name) throws AnalysisException, IOException {
        var p = Files.createTempFile("ecm_", ".tmp");
        Files.copy(is, p, StandardCopyOption.REPLACE_EXISTING);
        var f = p.toFile();
        var ff = analyze(f, name);

        if (!f.delete()) {
            f.deleteOnExit();
        }

        return ff;
    }

    public FileFormatDescriptor analyze(File f, String name) throws AnalysisException {
        try {
            var ff = new FileFormatDescriptor();
            ff.setIdentifiedAt(ZonedDateTime.now());

            var results = api.submit(f.toPath(), name);
            if (!results.isEmpty()) {
                ff.getItems().addAll(
                    results
                        .stream()
                        .map(r -> {
                            var item = new FileFormatDescriptor.FileFormatItem();
                            item.setName(r.getName());
                            item.setFormatVersion(r.getFormatVersion());
                            item.getMimeTypes().addAll(
                                Arrays.stream(r.getMimeType().split(", "))
                                    .map(StringUtils::stripToNull)
                                    .filter(Objects::nonNull)
                                    .toList()
                            );
                            item.setPuid(r.getPuid());
                            item.getExtensions().addAll(r.getExtensions());
                            item.setFileExtensionMismatch(r.isFileExtensionMismatch());
                            item.setMatchMethod(r.getMethod().toString());
                            item.setFormatIdentifier(FileFormatDescriptor.FormatIdentifier.DROID);
                            item.setResult(results.size() == 1 ? POSITIVE_SPECIFIC : TENTATIVE);
                            return item;
                        })
                        .toList()
                );
            }

            log.debug("File {} analyzed. Returning {}", name, ff);
            return ff;
        } catch (Exception e) {
            throw new AnalysisException(e);
        }
    }

    @Getter
    public static class MyApiResult extends ApiResult {
        private final String mimeType;
        private final String formatVersion;
        private final List<String> extensions;

        public MyApiResult(String extension, IdentificationMethod method, String puid, String name, boolean fileExtensionMismatch, String mimeType, String formatVersion, List<String> extensions) {
            super(extension, method, puid, name, fileExtensionMismatch);
            this.mimeType = mimeType;
            this.formatVersion = formatVersion;
            this.extensions = extensions;
        }
    }

    public static class DroidAPI {
        private static final String ZIP_PUID = "x-fmt/263";
        private static final String OLE2_PUID = "fmt/111";
        private static final AtomicLong ID_GENERATOR = new AtomicLong();
        private final BinarySignatureIdentifier droidCore;
        private final ContainerIdentifier zipIdentifier;
        private final ContainerIdentifier ole2Identifier;

        private DroidAPI(BinarySignatureIdentifier droidCore, ContainerIdentifier zipIdentifier, ContainerIdentifier ole2Identifier) {
            this.droidCore = droidCore;
            this.zipIdentifier = zipIdentifier;
            this.ole2Identifier = ole2Identifier;
        }

        public static DroidAPI getInstance(Path binarySignature, Path containerSignature) throws SignatureParseException {
            BinarySignatureIdentifier droidCore = new BinarySignatureIdentifier();
            droidCore.setSignatureFile(binarySignature.toAbsolutePath().toString());
            droidCore.init();
            droidCore.setMaxBytesToScan(Long.MAX_VALUE);
            droidCore.getSigFile().prepareForUse();
            ContainerApi containerApi = new ContainerApi(droidCore, containerSignature);
            return new DroidAPI(droidCore, containerApi.zipIdentifier(), containerApi.ole2Identifier());
        }

        public List<MyApiResult> submit(Path file, String filename) throws IOException {
            log.debug("Analyzing file {} with name {}", file, filename);
            RequestMetaData metaData = new RequestMetaData(Files.size(file), Files.getLastModifiedTime(file).toMillis(), filename);
            RequestIdentifier id = new RequestIdentifier(file.toAbsolutePath().toUri());
            id.setParentId(ID_GENERATOR.getAndIncrement());
            id.setNodeId(ID_GENERATOR.getAndIncrement());
            FileSystemIdentificationRequest request = new FileSystemIdentificationRequest(metaData, id);

            try {
                request.open(file);
                final String extension = request.getExtension();
                IdentificationResultCollection binaryResult = this.droidCore.matchBinarySignatures(request);
                Optional<String> containerPuid = this.getContainerPuid(binaryResult);
                IdentificationResultCollection resultCollection;
                if (containerPuid.isPresent()) {
                    log.debug("Processing container for file {}", file);
                    resultCollection = this.handleContainer(binaryResult, request, containerPuid.get());
                } else {
                    log.debug("Processing file {}", file);
                    this.droidCore.removeLowerPriorityHits(binaryResult);
                    this.droidCore.checkForExtensionsMismatches(binaryResult, request.getExtension());
                    if (binaryResult.getResults().isEmpty()) {
                        resultCollection = this.identifyByExtension(request);
                    } else {
                        resultCollection = binaryResult;
                    }
                }

                boolean fileExtensionMismatch = resultCollection.getExtensionMismatch();
                return resultCollection.getResults().stream()
                    .map(res -> {
                        var ff = this.droidCore.getSigFile().getFileFormat(res.getPuid());
                        var mimeType = res.getMimeType();
                        var version = res.getVersion();
                        var name = res.getName();
                        var ext = extension;
                        var extList = List.of(extension);
                        if (ff != null) {
                            ext = ff.getExtensions().stream().findFirst().orElse(extension);
                            extList = ff.getExtensions();

                            if (StringUtils.equalsIgnoreCase(res.getMethod().getMethod(), "Container")) {
                                mimeType = ff.getMimeType();
                                version = ff.getVersion();
                                name = ff.getName();
                            }
                        }

                        return new MyApiResult(ext, res.getMethod(), res.getPuid(), name, fileExtensionMismatch, mimeType, version, extList);
                    })
                    .toList();
            } catch (Throwable throwable) {
                try {
                    request.close();
                } catch (Throwable var11) {
                    throwable.addSuppressed(var11);
                }

                throw throwable;
            } finally {
                request.close();
            }
        }

        private IdentificationResultCollection identifyByExtension(FileSystemIdentificationRequest identificationRequest) {
            IdentificationResultCollection extensionResult = this.droidCore.matchExtensions(identificationRequest, false);
            this.droidCore.removeLowerPriorityHits(extensionResult);
            return extensionResult;
        }

        private Optional<String> getContainerPuid(IdentificationResultCollection binaryResult) {
            return binaryResult.getResults()
                .stream()
                .filter((x) -> ZIP_PUID.equals(x.getPuid()) || OLE2_PUID.equals(x.getPuid()))
                .map(IdentificationResult::getPuid)
                .findFirst();
        }

        private IdentificationResultCollection handleContainer(IdentificationResultCollection binaryResult, FileSystemIdentificationRequest identificationRequest, String containerPuid) throws IOException {
            ContainerIdentifier identifier = switch (containerPuid) {
                case ZIP_PUID -> this.zipIdentifier;
                case OLE2_PUID -> this.ole2Identifier;
                default -> throw new RuntimeException("Unknown container PUID : " + containerPuid);
            };

            IdentificationResultCollection containerResults = identifier.submit(identificationRequest);
            this.droidCore.removeLowerPriorityHits(containerResults);
            this.droidCore.checkForExtensionsMismatches(containerResults, identificationRequest.getExtension());
            containerResults.setFileLength(identificationRequest.size());
            containerResults.setRequestMetaData(identificationRequest.getRequestMetaData());
            return containerResults.getResults().isEmpty() ? binaryResult : containerResults;
        }
    }

}
