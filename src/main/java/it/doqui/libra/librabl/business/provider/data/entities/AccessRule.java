package it.doqui.libra.librabl.business.provider.data.entities;

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
