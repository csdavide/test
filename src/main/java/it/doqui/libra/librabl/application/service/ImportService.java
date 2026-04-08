package it.doqui.libra.librabl.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.octomix.josson.Josson;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.NodeInfoItem;
import it.doqui.libra.librabl.application.model.session.UserContextMap;
import it.doqui.libra.librabl.application.ports.in.*;
import it.doqui.libra.librabl.domain.model.files.ContentDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentProperty;
import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.mappers.AssociationMapper;
import it.doqui.libra.librabl.domain.model.exceptions.AbortException;
import it.doqui.libra.librabl.domain.ports.out.DictionaryRepository;
import it.doqui.libra.librabl.domain.service.MimeTypeService;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.graph.Identifier;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.application.model.ingest.*;
import it.doqui.libra.librabl.application.model.jobs.requests.ImportJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.ImportResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static it.doqui.libra.librabl.domain.policy.OperationOption.DISCARD_UNKOWN_PRESENT_METADATA;
import static it.doqui.libra.librabl.domain.policy.OperationOption.HANDLE_CONTENT_PROPERTIES;

@ApplicationScoped
@Slf4j
public class ImportService implements ImportUseCase {

    @Inject
    DictionaryRepository dictionaryRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NodeUseCase nodeService;

    @Inject
    ContentUseCase contentService;

    @Inject
    UserContextMap userContextMap;

    @Inject
    MimeTypeService mimeTypeService;

    @Inject
    DataStreamHandler dataStreamHandler;

    @Inject
    TemporaryUseCase temporaryUseCase;

    @Inject
    JobUseCase jobService;

    @Inject
    AssociationMapper associationMapper;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    private String[] findDataSetType(@NotNull String dataSetType) {
        return dictionaryRepository.getPayload("import.dataset", dataSetType).orElseThrow(() -> new NotFoundException("Unable to find transformation template: " + dataSetType)).split("---\n");
    }

    private List<LinkedInputNodeRequest> convertDataSet(String[] expArray, Map<String, Object> dataSet) {
        var requests = new ArrayList<LinkedInputNodeRequest>();
        if (expArray == null || expArray.length == 0) {
            return List.of(objectMapper.convertValue(dataSet, LinkedInputNodeRequest.class));
        } else {
            for (var exp : expArray) {
                if (StringUtils.isNotBlank(exp)) {
                    var josson = Josson.from(dataSet)
                            .customFunction("$md5()", jsonNode -> {
                                try {
                                    return TextNode.valueOf(ObjectUtils.hash(jsonNode.asText(), "MD5"));
                                } catch (NoSuchAlgorithmException e) {
                                    return null;
                                }
                            });

                    var result = josson.getNode(exp);
                    if (result == null) {
                        throw new SystemException("Unable to parse input dataset");
                    }

                    if (result.isArray()) {
                        result.forEach(record -> requests.add(convert(record)));
                    } else {
                        requests.add(convert(result));
                    }
                }
            }
        }

        return requests;
    }

    private LinkedInputNodeRequest convert(JsonNode input) {
        try {
            return objectMapper.treeToValue(input, LinkedInputNodeRequest.class);
        } catch (JsonProcessingException e) {
            throw new SystemException(e);
        }
    }

    private Map<String, Object> parseDataSet(InputStream inputStream, String contentType) {
        try {
            var typeRef = new TypeReference<HashMap<String, Object>>() {};
            final Map<String, Object> result;
            switch (contentType) {
                case MediaType.APPLICATION_XML, MediaType.TEXT_XML: {
                    var mapper = new XmlMapper();
                    result = mapper.readValue(IOUtils.readFully(inputStream), typeRef);
                    break;
                }
                case MediaType.APPLICATION_JSON: {
                    result = objectMapper.readValue(IOUtils.readFully(inputStream), typeRef);
                    break;
                }
                case "application/yaml", "application/yml", "text/yaml", "text/yml", "application/x-yaml",
                     "application/x-yml": {
                    var mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    result = mapper.readValue(IOUtils.readFully(inputStream), typeRef);
                    break;
                }
                default:
                    throw new BadRequestException("Unsupported content type: " + contentType);
            }

            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    @Override
    public ImportResult importDataSet(ImportSource source, ImportStatement statement, Consumer<Long> countConsumer) {
        MetadataStream ms;
        if (source instanceof ImportSource.NodeSource nodeSource) {
            ms = transactionManagerPort.perform(() -> {
                try {
                    var a = contentService.getNodeContent(nodeSource.getNode(), ContentReferenceable.of());
                    if (!(statement instanceof PackageImportStatement packageImportStatement)) {
                        return new MetadataStream(Files.newInputStream(a.getFile().toPath()), a.getContentProperty().getMimetype());
                    }

                    //TODO: valutare se controllare che sia zip e dare precondition failed
                    if (Strings.CI.equals(a.getContentProperty().getMimetype(), "application/zip")) {
                        log.warn("Source {} does not have mimetype application/zip as expected: it contains {}", source, a.getContentProperty().getMimetype());
                    }

                    userContextMap.getMap().put("zip", a);
                    return extractMetadata(a.getFile(), packageImportStatement.getMetadataRelativePath());
                } catch (IOException e) {
                    throw new SystemException(e);
                }
            });
        } else if (source instanceof ImportSource.FileSource fileSource) {
            if (!sessionContext.getUserContext().isUserInRole(UserContext.ROLE_SYS)) {
                throw new ForbiddenException("Only system users can import files from the file system");
            }

            try {
                var path = Paths.get(fileSource.getPath());
                if (statement instanceof PackageImportStatement packageImportStatement) {
                    //TODO: valutare se controllare che sia zip e dare precondition failed
                    if (Strings.CI.equals(fileSource.getMimeType(), "application/zip")) {
                        log.warn("Source {} does not have mimetype application/zip as expected: it contains {}", source, fileSource.getMimeType());
                    }

                    var file = path.toFile();
                    userContextMap.getMap().put("zip", path.toFile());
                    ms = extractMetadata(file, packageImportStatement.getMetadataRelativePath());
                } else {
                    ms = new MetadataStream(Files.newInputStream(path), fileSource.getMimeType());
                }
            } catch (IOException e) {
                throw new SystemException(e);
            }
        } else {
            throw new PreconditionFailedException("Unsupported import source type: " + source.getClass().getName());
        }

        var optionSet = optionSet(statement.getOptions());
        var expArray = Strings.CI.equals(statement.getTemplateName(), "identity")
                ? null
                : findDataSetType(statement.getTemplateName());
        var counter = new AtomicLong(0);
        var result = new ImportResult();
        if (statement instanceof StreamImportStatement streamImportStatement) {
            if (expArray == null) {
                scan(ms.inputStream(), streamImportStatement,
                        requests -> processRecords(requests, optionSet, statement.isPreview(), counter, result, countConsumer));
            } else {
                convertCSV(ms.inputStream(), streamImportStatement,
                        map -> processDataSet(map, expArray, optionSet, statement.isPreview(), counter, result, countConsumer));
            }
        } else if (statement instanceof PackageImportStatement || statement instanceof MetadataImportStatement) {
            var map = parseDataSet(ms.inputStream(), ms.contentType());
            var tx = processDataSet(map, expArray, optionSet, statement.isPreview(), counter, result, null);
            result.setTx(tx);
        } else {
            throw new SystemException("Unsupported import statement type: " + statement.getClass().getName());
        }

        result.setAffectedNodes(counter.get());
        return result;
    }

    private Long processDataSet(Map<String, Object> map, String[] expArray, Set<OperationOption> options, boolean preview, AtomicLong counter, ImportResult result, Consumer<Long> countConsumer) {
        var requests = convertDataSet(expArray, map);
        return processRecords(requests, options, preview, counter, result, countConsumer);
    }

    private Long processRecords(List<LinkedInputNodeRequest> requests, Set<OperationOption> options, boolean preview, AtomicLong counter, ImportResult result, Consumer<Long> countConsumer) {
        final long count;
        final Long tx;
        if (preview) {
            result.getConvertedInputs().addAll(requests);
            count = requests.size();
            tx = null;
        } else {
            var r = performImport(requests, options);
            count = r.identifiers().size();
            tx = r.tx();
        }

        counter.addAndGet(count);
        long n = counter.get();
        log.trace("{} records totally processed", n);
        if (countConsumer != null) {
            countConsumer.accept(n);
        }

        return tx;
    }

    private MetadataStream extractMetadata(File file, String metadataFilePath) throws IOException {
        final byte[] bytes;
        final String contentType;
        try (ZipFile zipFile = new ZipFile(file)) {
            var zipEntry = zipFile.getEntry(metadataFilePath);
            if (zipEntry == null) {
                throw new PreconditionFailedException("Source does not contain metadata file " + metadataFilePath);
            }

            bytes = IOUtils.readFully(zipFile.getInputStream(zipEntry));
            contentType = Optional.ofNullable(mimeTypeService.getMimeType(zipEntry.getName()))
                    .orElseThrow(() -> new PreconditionFailedException("Unsupported mime type for file " + zipEntry.getName()));
        }

        return new MetadataStream(new ByteArrayInputStream(bytes), contentType);
    }

    @Override
    public JobResponse submitImportDataSet(InputStream inputStream, String mimeType, Duration duration, ImportStatement statement) {
        var descriptor = new ContentDescriptor();
        descriptor.setMimetype(mimeType);
        var ephemeralContent = temporaryUseCase.createEphemeralNode(descriptor, inputStream, duration);

        var importSource = new ImportSource.NodeSource();
        importSource.setNode(new Vertex(VertexType.UUID, ephemeralContent.getUuid()));

        var importJobRequest = new ImportJobRequest();
        importJobRequest.setSource(importSource);
        importJobRequest.setImportStatement(statement);
        importJobRequest.setMode(OperationMode.ASYNC);

        return jobService.executeJob(importJobRequest);
    }

    private Set<OperationOption> optionSet(Set<OperationOption> additionalOptions) {
        var optionSet = new HashSet<>(additionalOptions);
        optionSet.add(HANDLE_CONTENT_PROPERTIES);
        optionSet.add(DISCARD_UNKOWN_PRESENT_METADATA);
        return optionSet;
    }

    private ImportTaskResult performImport(List<LinkedInputNodeRequest> requests, @NotNull Set<OperationOption> optionSet) {
        var cSet = requests.stream()
            .flatMap(input -> dataStreamHandler.downloadStreams(input).stream())
            .toList();

        return transactionManagerPort.perform(tx -> {
            var options = transactionManagerPort.options();
            cSet.stream().map(ContentProperty::getContentUrl).filter(Objects::nonNull).forEach(options::registerCreatedContentUrl);

            var nodes = requests.stream()
                .peek(this::accept)
                .filter(r -> !Strings.CS.startsWith(r.getTypeName(),"$:"))
                .filter(this::hasParent)
                .map(r -> {
                    var uuid = nodeService.createOrUpdateNode(r, optionSet);
                    return NodeInfoItem.builder().uuid(uuid).build();
                })
                .filter(Objects::nonNull)
                .toList();

            return PerformResult.<ImportTaskResult>builder()
                .mode(PerformResult.Mode.SYNC)
                .priorityUUIDs(nodes.stream().map(Identifier::getUuid).collect(Collectors.toUnmodifiableSet()))
                .result(new ImportTaskResult(nodes, tx.getId()))
                .build();
        });
    }

    private boolean hasParent(LinkedInputNodeRequest input) {
        if (input.getLink() != null) {
            return StringUtils.isNotBlank(input.getLink().getName());
        } else if (input.getAssociations() != null) {
            return input.getAssociations().stream().anyMatch(a -> StringUtils.isNotBlank(a.getName()));
        }

        return false;
    }

    private void accept(LinkedInputNodeRequest r) {
        if (Strings.CS.equals(r.getTypeName(), "$:file-ref")) {
            try {
                fillFileRefMap(r.getLink());
                for (var association : r.getAssociations()) {
                    fillFileRefMap(associationMapper.mapLinkItemRequest(association));
                } // end for
            } catch (IOException e) {
                throw new SystemException(e);
            }
        } // end if the type has namespace "$"
    }

    private void fillFileRefMap(ParentLink link) throws IOException {
        if (link == null) {
            return;
        }

        var v = link.getParent();
        if (v != null) {
            var a = contentService.getNodeContent(v, ContentReferenceable.of());
            var key = Optional.ofNullable(link.getName())
                    .orElseGet(() -> v.getType() == VertexType.PATH
                            ? String.valueOf(userContextMap.getMap().size())
                            : v.getValue());
            userContextMap.getMap().put(key, a);
        }
    }

    private void convertCSV(InputStream inputStream, StreamImportStatement statement, Consumer<Map<String, Object>> consumer) {
        final int limit = statement.getLimit();
        final int blockSize = statement.getBlockSize();
        final char csvSeparator = Optional.ofNullable(statement.getCsvSeparator()).map(Object::toString).map(s -> s.charAt(0)).orElse(';');
        int skip = statement.getSkip();
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(csvSeparator).get();
        try (var parser = CSVParser.parse(inputStream, StandardCharsets.UTF_8, format)) {
            int count = 0;
            var records = new ArrayList<Map<String, List<String>>>();
            boolean checkBom = false;
            for (var line : parser) {
                if (sessionContext.getCancelled().get()) {
                    throw new AbortException("Import cancelled: job " + sessionContext.getJobId());
                }

                if (skip > 0) {
                    skip--;
                    continue;
                } else if (count == 0 && line.size() > 0) {
                    checkBom = true;
                }

                if (limit >= 0 && count >= limit) {
                    break;
                }

                count++;

                if (line.size() < 1) {
                    continue;
                }

                var fields = new ArrayList<String>();
                for (int i = 0; i < line.size(); i++) {
                    var s = line.get(i);
                    if (checkBom && line.get(0).startsWith("\ufeff")) {
                        s = s.substring(1);
                        checkBom = false;
                    }

                    fields.add(s.trim());
                }

                records.add(Map.of("fields", fields));
                if (blockSize > 0 && records.size() >= blockSize) {
                    if (consumer != null) {
                        consumer.accept(Map.of("records", records));
                    }

                    records = new ArrayList<>();
                }

            }

            if (consumer != null && !records.isEmpty()) {
                consumer.accept(Map.of("records", records));
            }
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    private void scan(InputStream inputStream, StreamImportStatement statement, Consumer<List<LinkedInputNodeRequest>> consumer) {
        final int limit = statement.getLimit();
        final int blockSize = statement.getBlockSize();
        int skip = statement.getSkip();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int count = 0;
            var records = new ArrayList<LinkedInputNodeRequest>();
            for (String line; (line = reader.readLine()) != null; ) {
                if (skip > 0) {
                    skip--;
                    continue;
                } else if (count == 0 && line.startsWith("\ufeff")) {
                    line = line.substring(1);
                }

                if (limit >= 0 && count >= limit) {
                    break;
                }

                if (StringUtils.isNotBlank(line)) {
                    final LinkedInputNodeRequest input;
                    try {
                        input = objectMapper.readValue(line, LinkedInputNodeRequest.class);
                    } catch (Exception e) {
                        log.warn("Unable to parse line number {}", count);
                        throw new SystemException(e);
                    }

                    records.add(input);
                    if (blockSize > 0 && records.size() >= blockSize) {
                        if (consumer != null) {
                            consumer.accept(records);
                        }

                        records = new ArrayList<>();
                    }
                }

                count++;
            }

            if (consumer != null && !records.isEmpty()) {
                consumer.accept(records);
            }
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    private record MetadataStream(InputStream inputStream, String contentType) {}
    private record ImportTaskResult(List<? extends Identifier> identifiers, Long tx) {}
}
