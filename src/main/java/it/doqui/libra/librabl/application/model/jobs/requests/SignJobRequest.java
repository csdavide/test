package it.doqui.libra.librabl.application.model.jobs.requests;

import it.doqui.libra.librabl.application.model.document.SignStatement;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class SignJobRequest extends JobRequest {
    private SignStatement sign;

    public SignJobRequest() {
        super("sign");
    }
}
