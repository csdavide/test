package it.doqui.libra.librabl.foundation.exceptions;

public class BadQueryException extends BadRequestException {
    public BadQueryException() {
    }

    public BadQueryException(String message) {
        super(message);
    }
}
