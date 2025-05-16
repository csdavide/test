package it.doqui.libra.librabl.foundation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NodeRef implements NodeReferenceable {
    private String uuid;
    private String tenant;
}
