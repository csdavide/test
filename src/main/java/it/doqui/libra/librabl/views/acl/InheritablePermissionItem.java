package it.doqui.libra.librabl.views.acl;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = PermissionItem.class)
public class InheritablePermissionItem extends PermissionItem {
    boolean inherited;
}
