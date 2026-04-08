package it.doqui.libra.librabl.application.model.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PkItem {
    private String kid;
    private String key;
    private String username;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Set<String> scopes;

    public PkItem() {
        this.scopes = new HashSet<>();
    }
}
