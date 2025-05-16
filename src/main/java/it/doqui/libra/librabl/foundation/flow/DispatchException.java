package it.doqui.libra.librabl.foundation.flow;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchException extends RuntimeException {
    private String className;
    private String method;
    public DispatchException(Throwable e) {
        super(e);
    }
}
