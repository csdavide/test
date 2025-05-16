package it.doqui.libra.librabl.views.security;

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
public class PkRequest {
    private String key;
    private String username;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Set<String> scopes;

    public PkRequest() {
        this.scopes = new HashSet<>();
    }
}
