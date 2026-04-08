package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
public class SecurityGroup {

    private Long id;
    private String tenant;
    private String uuid;
    private String name;
    private boolean inheritanceEnabled;
    private boolean managed;
    private ApplicationTransaction tx;

    @JsonIgnore
    private final List<AccessRule> rules;

    public SecurityGroup() {
        this.rules = new LinkedList<>();
    }
}
