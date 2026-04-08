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
public class ReindexJobRequest extends JobRequest {

    public ReindexJobRequest() {
        super("reindex");
    }

    private String tenant;
    private Vertex node;
    private NodeReindexRequest reindex;

    @Getter
    @Setter
    public static class NodeReindexRequest {
        private boolean recursive;
        private int blockSize;
    }

}
