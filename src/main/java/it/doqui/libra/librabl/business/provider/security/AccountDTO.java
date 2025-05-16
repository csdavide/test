package it.doqui.libra.librabl.business.provider.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class AccountDTO {
    private String key;
    private AccountDataDTO data;

    @Getter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AccountDataDTO {
        private String password;
        private String alg;
        private String appKeyName;
        private String defaultAuthority;

        private final List<String> rules;
        private final Set<String> roles;

        AccountDataDTO() {
            rules = new ArrayList<>();
            roles = new HashSet<>();
        }
    }
}
