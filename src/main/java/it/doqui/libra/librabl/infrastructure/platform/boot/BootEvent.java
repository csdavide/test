package it.doqui.libra.librabl.infrastructure.platform.boot;

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
