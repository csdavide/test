package it.doqui.libra.librabl.application.model.jobs.requests;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Duration;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobRequest.class)
public class ExportJobRequest extends FilteredJobRequest {
    private FileNameFormat fileNameFormat = FileNameFormat.NAME;
    private Duration duration = Duration.ofDays(1);

    public ExportJobRequest() {
        super("export");
    }

    public enum FileNameFormat {
        UUID,
        NAME
    }
}
