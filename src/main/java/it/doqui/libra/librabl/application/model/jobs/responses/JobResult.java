package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import it.doqui.libra.librabl.application.model.document.DocumentOperationResponse;
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
        property = "kind", // discriminator
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UUIDResult.class, name = "create"),
        @JsonSubTypes.Type(value = CountResult.class, name = "update"),
        @JsonSubTypes.Type(value = CountResult.class, name = "delete"),
        @JsonSubTypes.Type(value = CountResult.class, name = "link"),
        @JsonSubTypes.Type(value = CountResult.class, name = "unlink"),
        @JsonSubTypes.Type(value = UUIDResult.class, name = "copy"),
        @JsonSubTypes.Type(value = CountResult.class, name = "move"),
        @JsonSubTypes.Type(value = CountResult.class, name = "rename"),
        @JsonSubTypes.Type(value = CountResult.class, name = "restore"),
        @JsonSubTypes.Type(value = VersionResult.class, name = "version"),
        @JsonSubTypes.Type(value = CountResult.class, name = "replace"),
        @JsonSubTypes.Type(value = ImportResult.class, name = "import"),
        @JsonSubTypes.Type(value = ExportResult.class, name = "export"),
        @JsonSubTypes.Type(value = DocumentOperationResponse.class, name = "sign"),
        @JsonSubTypes.Type(value = CalculatedVolumesResult.class, name = "volume"),
        @JsonSubTypes.Type(value = ProvisionResult.class, name = "provision"),
        @JsonSubTypes.Type(value = ReindexJobResult.class, name = "reindex"),
        @JsonSubTypes.Type(value = JobListResult.class, name = "jobs")
})
@Schema(
        // Indica quale campo fa da discriminatore per OpenAPI
        discriminatorProperty = "kind",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "create", schema = UUIDResult.class),
                @DiscriminatorMapping(value = "update", schema = CountResult.class),
                @DiscriminatorMapping(value = "delete", schema = CountResult.class),
                @DiscriminatorMapping(value = "link", schema = CountResult.class),
                @DiscriminatorMapping(value = "unlink", schema = CountResult.class),
                @DiscriminatorMapping(value = "copy", schema = UUIDResult.class),
                @DiscriminatorMapping(value = "move", schema = CountResult.class),
                @DiscriminatorMapping(value = "rename", schema = CountResult.class),
                @DiscriminatorMapping(value = "restore", schema = CountResult.class),
                @DiscriminatorMapping(value = "version", schema = VersionResult.class),
                @DiscriminatorMapping(value = "replace", schema = CountResult.class),
                @DiscriminatorMapping(value = "import", schema = ImportResult.class),
                @DiscriminatorMapping(value = "export", schema = ExportResult.class),
                @DiscriminatorMapping(value = "sign", schema = DocumentOperationResponse.class),
                @DiscriminatorMapping(value = "volume", schema = CalculatedVolumesResult.class),
                @DiscriminatorMapping(value = "provision", schema = ProvisionResult.class),
                @DiscriminatorMapping(value = "reindex", schema = ReindexJobResult.class),
                @DiscriminatorMapping(value = "jobs", schema = JobListResult.class)
        }
)
public abstract class JobResult {
    @Schema(required = true, examples = {"create","update","delete"})
    private String kind;
}
