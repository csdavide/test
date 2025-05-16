package it.doqui.libra.librabl.business.provider.data.entities;

import java.time.ZonedDateTime;

public interface GraphNode {
    Long getId();
    Integer getVersion();
    String getTenant();
    String getUuid();
    String getTypeName();
    NodeData getData();
    ZonedDateTime getUpdatedAt();

    SecurityGroup getSecurityGroup();
}
