package it.doqui.libra.librabl.business.provider.integration.solr;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SolrField {
    private String name;
    private String type;
    private boolean multiValued;
    private boolean indexed;
    private boolean stored;
}
