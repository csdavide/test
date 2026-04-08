package it.doqui.libra.librabl.application.model.jobs.requests;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Set;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class SchemaUpgradeJobRequest extends JobRequest {

    @JsonProperty("schema")
    @JsonAlias("schemas")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private Set<String> schema;
    private int fromVersion;
    private int targetVersion;

    public SchemaUpgradeJobRequest() {
        super("schema-upgrade");
    }
}
