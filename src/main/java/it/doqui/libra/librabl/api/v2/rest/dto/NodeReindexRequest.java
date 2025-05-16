package it.doqui.libra.librabl.api.v2.rest.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeReindexRequest {
    private boolean recursive;
    private int blockSize;
    private int priority;
}
