package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ToString
public class User {
    private Long id;
    private String tenant;
    private String uuid;
    private String username;
    private final UserData data;
    private final Set<UserGroup> groups;

    public User() {
        data = new UserData();
        groups = new LinkedHashSet<>();
    }

}
