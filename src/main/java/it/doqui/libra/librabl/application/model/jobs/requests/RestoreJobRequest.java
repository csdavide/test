package it.doqui.libra.librabl.application.model.jobs.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class RestoreJobRequest extends JobRequest {

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RestoreStatement {
        private Vertex node;
        private ParentLink destination;
        private LinkMode linkMode;
    }

    private RestoreStatement restore;

    public RestoreJobRequest() {
        super("restore");
    }
}
