package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class TransactionalNodesItem {
    private Long[] nodesArray;
    private Long tx;
}
