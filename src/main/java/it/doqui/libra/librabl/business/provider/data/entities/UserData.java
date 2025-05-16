package it.doqui.libra.librabl.business.provider.data.entities;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class UserData implements Serializable {

    @JsonProperty("password")
    private String password;

    @JsonProperty("alg")
    private String alg;

    @JsonProperty("home")
    private String home;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("enabled")
    private Boolean enabled = true;

    @JsonProperty("locked")
    @JsonAlias("accountLocked")
    private boolean locked;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<String> roles;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<String> authorizedGuests;

    public UserData() {
        this.roles = new HashSet<>();
        this.authorizedGuests = new HashSet<>();
    }

    public void copy(UserData u) {
        this.password = u.password;
        this.alg = u.alg;
        this.home = u.home;
        this.firstName = u.firstName;
        this.lastName = u.lastName;
        this.enabled = u.enabled;
        this.roles.clear();
        this.roles.addAll(u.roles);
        this.authorizedGuests.clear();
        this.authorizedGuests.addAll(u.authorizedGuests);
    }
}
