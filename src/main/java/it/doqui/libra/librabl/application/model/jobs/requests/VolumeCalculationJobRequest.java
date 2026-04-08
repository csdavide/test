package it.doqui.libra.librabl.application.model.jobs.requests;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class VolumeCalculationJobRequest extends JobRequest {
    public VolumeCalculationJobRequest() {
        super("volume");
    }
}
