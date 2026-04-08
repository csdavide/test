package it.doqui.libra.librabl.application.model.jobs.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplaceStatement {
    private Vertex node;
    private Integer version;
}
