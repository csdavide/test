package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobData {
    private String authority;
    private Set<String> roles;
    private JobRequest request;
    private String message;
    private JobResult result;
}
