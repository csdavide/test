package it.doqui.libra.librabl.application.model.jobs.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.doqui.libra.librabl.application.model.ingest.ImportSource;
import it.doqui.libra.librabl.application.model.ingest.ImportStatement;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class ImportJobRequest extends JobRequest {
    private ImportSource source;

    @JsonProperty("import")
    private ImportStatement importStatement;

    public ImportJobRequest() {
        super("import");
    }
}
