package it.doqui.libra.librabl.foundation.exceptions;

public class BadDataException extends BadRequestException {
    public BadDataException() {
    }

    public BadDataException(String message) {
        super(message);
    }
}
