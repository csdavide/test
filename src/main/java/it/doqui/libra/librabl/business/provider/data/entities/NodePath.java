package it.doqui.libra.librabl.business.provider.data.entities;

import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodePath {

    Long id;
    private ActiveNode node;
    private String path;
    private String filePath;
    private String sgPath;
    private int lev;
    private boolean hard;
    private ActiveNode parent;
    private ApplicationTransaction tx;
}
