package it.doqui.libra.librabl.application.model.ingest;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type", // discriminator
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ImportSource.NodeSource.class, name = "node"),
        @JsonSubTypes.Type(value = ImportSource.FileSource.class, name = "file")
})
@Schema(
        oneOf = {
                ImportSource.NodeSource.class,
                ImportSource.FileSource.class
        },
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "node", schema = ImportSource.NodeSource.class),
                @DiscriminatorMapping(value = "file", schema = ImportSource.FileSource.class)
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ImportSource {
    @Schema(required = true, examples = {"file","node"})
    private final String type;

    public ImportSource(String type) {
        this.type = type;
    }

    @Getter
    @Setter
    @ToString(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(allOf = ImportSource.class)
    public static class FileSource extends ImportSource {
        private String path;

        @JsonProperty("mimeType")
        @JsonAlias("mimetype")
        private String mimeType;

        public FileSource() {
            super("file");
        }
    }

    @Getter
    @Setter
    @ToString(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(allOf = ImportSource.class)
    public static class NodeSource extends ImportSource {
        private Vertex node;

        public NodeSource() {
            super("node");
        }
    }
}
