package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class AccessRule {

    private Long id;
    private String authority;
    private String rights;
    private SecurityGroup securityGroup;
}
