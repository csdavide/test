package it.doqui.libra.librabl.application.model.graph;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NodePathItem {
    private String path;
    private String route;
    private boolean hard;
}
