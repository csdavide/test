package it.doqui.libra.librabl.views.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = UserItem.class)
public class DetailedUserItem extends UserItem {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<String> groups;

    public DetailedUserItem() {
        this.groups = new HashSet<>();
    }
}
