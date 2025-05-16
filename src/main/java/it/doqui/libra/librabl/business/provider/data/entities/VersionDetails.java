package it.doqui.libra.librabl.business.provider.data.entities;

import it.doqui.libra.librabl.views.version.VersionItem;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VersionDetails {
    private VersionItem item;
    private NodeData data;
}
