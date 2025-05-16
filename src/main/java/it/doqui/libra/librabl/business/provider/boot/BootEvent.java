package it.doqui.libra.librabl.business.provider.boot;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class BootEvent {
    private Map<?,?> attributes;

    public BootEvent() {
    }

}
