package it.doqui.libra.librabl.business.service.node;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class SortDefinition {
    private String fieldName;
    private boolean ascending;
}
