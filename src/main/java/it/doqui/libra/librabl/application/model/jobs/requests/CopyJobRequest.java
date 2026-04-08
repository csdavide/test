package it.doqui.libra.librabl.application.model.jobs.requests;

import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.policy.CopyMode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class CopyJobRequest extends JobRequest {

    @Getter
    @Setter
    @ToString
    public static class CopyStatement {
        private boolean copyChildren;
        private boolean excludeAssociations;
        private CopyMode copyMode;
    }

    private Vertex vertex;
    private ParentLink link;
    private CopyStatement copy;

    public CopyJobRequest() {
        super("copy");
    }
}
