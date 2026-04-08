package it.doqui.libra.librabl.application.model.management;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VolumeInfo {
    private ZonedDateTime timestamp;
    private String tenant;
    private String schema;
    private long size;
    private long fileCount;
    private long nodeCount;
    private long contentCount;
    private long archivedNodeCount;
    private long archivedContentCount;
}
