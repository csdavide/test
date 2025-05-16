package it.doqui.libra.librabl.views.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = AbstractUserItem.class)
public class UserItem extends AbstractUserItem {
    private String homeUUID;
    private String homePath;

    @JsonSetter(nulls = Nulls.SKIP)
    private boolean enabled = true;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean locked;
}
