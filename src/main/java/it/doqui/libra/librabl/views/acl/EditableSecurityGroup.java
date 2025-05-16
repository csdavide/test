package it.doqui.libra.librabl.views.acl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Optional;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = PermissionsList.class)
public abstract class EditableSecurityGroup extends PermissionsList {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<String> name;
}
