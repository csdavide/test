package it.doqui.libra.librabl.business.provider.data.entities;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ToString
public class UserGroup {
    private Long id;
    private String tenant;
    private String groupname;
    private final Set<User> users;

    public UserGroup() {
        users = new LinkedHashSet<>();
    }
}
