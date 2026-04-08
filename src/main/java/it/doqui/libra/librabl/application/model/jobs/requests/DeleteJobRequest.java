package it.doqui.libra.librabl.application.model.jobs.requests;

import it.doqui.libra.librabl.domain.policy.DeleteMode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class DeleteJobRequest extends FilteredJobRequest {
    private DeleteMode deleteMode = DeleteMode.DELETE;

    public DeleteJobRequest() {
        super("delete");
    }
}
