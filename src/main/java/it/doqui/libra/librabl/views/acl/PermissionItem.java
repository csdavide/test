package it.doqui.libra.librabl.views.acl;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PermissionItem {
    private String authority;
    private String rights;
}
