package it.doqui.libra.librabl.application.model.jobs.requests;

import it.doqui.libra.librabl.application.model.tenant.TenantCreationRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class ProvisionJobRequest extends JobRequest {

    public ProvisionJobRequest() {
        super("provision");
    }

    private TenantCreationRequest provision;
}
