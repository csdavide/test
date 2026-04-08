package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import it.doqui.libra.librabl.domain.model.files.FileMetadata;
import it.doqui.libra.librabl.foundation.serialization.UriStringDeserializer;
import it.doqui.libra.librabl.domain.model.document.SignData;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileData implements FileMetadata {
    @JsonDeserialize(using = UriStringDeserializer.class)
    private String contentUrl;
    private String hash;
    private Long size;

    protected String name;

    @JsonProperty("mimetype") @JsonAlias("mimeType")
    private String mimetype;
    private String encoding;
    private String locale;
    private String fileName;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean opaque;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<SignData> signs = new ArrayList<>();

    @JsonIgnore
    private transient String text;

    @Override
    @JsonIgnore
    public URI getFileURI() {
        return Optional.ofNullable(contentUrl).map(URI::create).orElse(null);
    }
}
