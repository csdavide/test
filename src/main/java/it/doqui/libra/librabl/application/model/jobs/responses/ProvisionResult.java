package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.tenant.TenantItem;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = JobResult.class)
public class ProvisionResult extends JobResult {
    private TenantItem provisionedTenant;
}
