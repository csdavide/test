package it.doqui.libra.librabl.application.model.jobs.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class VersionJobRequest extends JobRequest {

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VersionStatement {
        private String tag;
    }

    private Vertex node;
    private VersionStatement version;

    public VersionJobRequest() {
        super("version");
    }
}
