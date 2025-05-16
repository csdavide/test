package it.doqui.libra.librabl.views.management;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
public class VolumeInfo {
    private ZonedDateTime timestamp;
    private String tenant;
    private long size;
    private long fileCount;
    private long nodeCount;
    private long contentCount;
    private long archivedNodeCount;
    private long archivedContentCount;
}
