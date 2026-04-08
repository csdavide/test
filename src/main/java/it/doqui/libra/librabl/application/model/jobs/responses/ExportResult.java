package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.net.URI;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobResult.class)
public class ExportResult extends JobResult implements PartiallyCompletable {
    private long processedNodes;
    private long requestedFiles;
    private long aggregatedFileCount;
    private URI downloadUri;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private double completeness;

    @JsonIgnore
    private boolean aborted;

    @JsonIgnore
    private boolean completed;
}
