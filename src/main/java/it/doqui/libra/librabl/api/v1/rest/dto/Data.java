package it.doqui.libra.librabl.api.v1.rest.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Data {
    private Object output;

    public Data() {
    }

    public Data(Object output) {
        this.output = output;
    }

}