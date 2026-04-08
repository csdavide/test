package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = JobResult.class)
public class VersionResult extends JobResult {

    @Getter
    @Setter
    @ToString(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VersionInfo {
        private String uuid;
        private int version;
        private String tag;
        private ZonedDateTime createdAt;
        private String createdBy;
    }

    private NodeItem node;
    private VersionInfo version;
}
