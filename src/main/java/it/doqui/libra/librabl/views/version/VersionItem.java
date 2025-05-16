package it.doqui.libra.librabl.views.version;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.views.node.NodeItem;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionItem {

    private String versionUUID;
    private long nodeId;
    private int version;
    private String nodeUUID;
    private String versionTag;
    private ZonedDateTime createdAt;
    private String createdBy;
    private NodeItem item;

}
