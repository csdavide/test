package it.doqui.libra.librabl.application.model.jobs.requests;

import it.doqui.libra.librabl.domain.model.graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class ReplaceJobRequest extends JobRequest {

    private Vertex node;
    private ReplaceStatement replace;

    public ReplaceJobRequest() {
        super("replace");
    }
}
