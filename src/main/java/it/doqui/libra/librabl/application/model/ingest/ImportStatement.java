package it.doqui.libra.librabl.application.model.ingest;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.HashSet;
import java.util.Set;

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
        @JsonSubTypes.Type(value = PackageImportStatement.class, name = "package"),
        @JsonSubTypes.Type(value = StreamImportStatement.class, name = "stream"),
        @JsonSubTypes.Type(value = MetadataImportStatement.class, name = "metadata")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "package", schema = PackageImportStatement.class),
                @DiscriminatorMapping(value = "stream", schema = StreamImportStatement.class),
                @DiscriminatorMapping(value = "metadata", schema = MetadataImportStatement.class)
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ImportStatement {
    @Schema(required = true, examples = {"package","stream","metadata"})
    @JsonSetter(nulls = Nulls.SKIP)
    private final String type;
    private String templateName;
    private boolean preview;

    @Parameter(
            description = "List of comma separated options to alter the behaviour",
            schema = @Schema(type = SchemaType.ARRAY, implementation = String.class, enumeration = {"HANDLE_CONTENT_PROPERTIES", "DISCARD_UNKOWN_PRESENT_METADATA", "IGNORE_INVALID_CONTENT"})
    )
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<OperationOption> options;

    protected ImportStatement(String type) {
        this.type = type;
        this.options = new HashSet<>();
    }

}
