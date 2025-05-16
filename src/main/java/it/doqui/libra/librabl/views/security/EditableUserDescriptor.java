package it.doqui.libra.librabl.views.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Optional;

@SuppressWarnings("ALL")
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditableUserDescriptor {
    private Optional<char[]> password;
    private Optional<String> firstName;
    private Optional<String> lastName;
    private Optional<Boolean> enabled;
}
