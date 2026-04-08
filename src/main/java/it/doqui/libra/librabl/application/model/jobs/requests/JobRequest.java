package it.doqui.libra.librabl.application.model.jobs.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import it.doqui.libra.librabl.foundation.DurationFilter;
import it.doqui.libra.librabl.foundation.OperationMode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Duration;

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
        @JsonSubTypes.Type(value = CreateJobRequest.class, name = "create"),
        @JsonSubTypes.Type(value = UpdateJobRequest.class, name = "update"),
        @JsonSubTypes.Type(value = DeleteJobRequest.class, name = "delete"),
        @JsonSubTypes.Type(value = LinkJobRequest.class, name = "link"),
        @JsonSubTypes.Type(value = UnlinkJobRequest.class, name = "unlink"),
        @JsonSubTypes.Type(value = CopyJobRequest.class, name = "copy"),
        @JsonSubTypes.Type(value = MoveJobRequest.class, name = "move"),
        @JsonSubTypes.Type(value = RenameJobRequest.class, name = "rename"),
        @JsonSubTypes.Type(value = RestoreJobRequest.class, name = "restore"),
        @JsonSubTypes.Type(value = VersionJobRequest.class, name = "version"),
        @JsonSubTypes.Type(value = ReplaceJobRequest.class, name = "replace"),
        @JsonSubTypes.Type(value = ImportJobRequest.class, name = "import"),
        @JsonSubTypes.Type(value = ExportJobRequest.class, name = "export"),
        @JsonSubTypes.Type(value = SignJobRequest.class, name = "sign"),
        @JsonSubTypes.Type(value = VolumeCalculationJobRequest.class, name = "volume"),
        @JsonSubTypes.Type(value = ProvisionJobRequest.class, name = "provision"),
        @JsonSubTypes.Type(value = ReindexJobRequest.class, name = "reindex"),
        @JsonSubTypes.Type(value = SchemaUpgradeJobRequest.class, name = "schema-upgrade"),
        @JsonSubTypes.Type(value = JobListRequest.class, name = "jobs")
})
@Schema(
        // Definisce che JobRequest può essere uno dei seguenti tipi
        // Indica quale campo fa da discriminatore per OpenAPI
        discriminatorProperty = "kind",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "create", schema = CreateJobRequest.class),
                @DiscriminatorMapping(value = "update", schema = UpdateJobRequest.class),
                @DiscriminatorMapping(value = "delete", schema = DeleteJobRequest.class),
                @DiscriminatorMapping(value = "link", schema = LinkJobRequest.class),
                @DiscriminatorMapping(value = "unlink", schema = UnlinkJobRequest.class),
                @DiscriminatorMapping(value = "copy", schema = CopyJobRequest.class),
                @DiscriminatorMapping(value = "move", schema = MoveJobRequest.class),
                @DiscriminatorMapping(value = "rename", schema = RenameJobRequest.class),
                @DiscriminatorMapping(value = "restore", schema = RestoreJobRequest.class),
                @DiscriminatorMapping(value = "version", schema = VersionJobRequest.class),
                @DiscriminatorMapping(value = "replace", schema = ReplaceJobRequest.class),
                @DiscriminatorMapping(value = "import", schema = ImportJobRequest.class),
                @DiscriminatorMapping(value = "export", schema = ExportJobRequest.class),
                @DiscriminatorMapping(value = "sign", schema = SignJobRequest.class),
                @DiscriminatorMapping(value = "volume", schema = VolumeCalculationJobRequest.class),
                @DiscriminatorMapping(value = "provision", schema = ProvisionJobRequest.class),
                @DiscriminatorMapping(value = "reindex", schema = ReindexJobRequest.class),
                @DiscriminatorMapping(value = "schema-upgrade", schema = SchemaUpgradeJobRequest.class),
                @DiscriminatorMapping(value = "jobs", schema = JobListRequest.class)
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class JobRequest {
    @Schema(required = true, examples = {"create","update","delete"})
    private final String kind;
    private OperationMode mode = OperationMode.SYNC;

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = DurationFilter.class)
    private Duration delay = Duration.ZERO;

    protected JobRequest(String kind) {
        this.kind = kind;
    }

}
