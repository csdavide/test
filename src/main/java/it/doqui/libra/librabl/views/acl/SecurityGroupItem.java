package it.doqui.libra.librabl.views.acl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = EditableSecurityGroup.class)
public class SecurityGroupItem extends EditableSecurityGroup {

    @JsonProperty("sgid")
    private String sgID;
}
