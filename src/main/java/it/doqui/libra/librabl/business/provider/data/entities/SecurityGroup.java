package it.doqui.libra.librabl.business.provider.data.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
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
