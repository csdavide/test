package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = JobResult.class)
public class ReindexJobResult extends JobResult {
    private long indexedTransactions;
    private long totalExpectedTransactions;

    public ReindexJobResult() {
        setKind("reindex");
    }
}
