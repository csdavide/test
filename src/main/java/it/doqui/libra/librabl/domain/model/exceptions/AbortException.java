package it.doqui.libra.librabl.domain.model.exceptions;

public class AbortException extends RuntimeException {
    public AbortException(String message) {
        super(message);
    }
}
