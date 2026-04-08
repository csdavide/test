package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto;

import it.doqui.libra.librabl.foundation.Named;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NamedItem implements Named {
    private String name;
}
