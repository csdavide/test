package it.doqui.libra.librabl.application.model.jobs.responses;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobResult.class)
public class CountResult extends JobResult {
    private long affectedNodes;
}
