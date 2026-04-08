package it.doqui.libra.librabl.foundation.exceptions;

public class ConflictException extends WebException {

    public ConflictException() {
        super(409);
    }

    public ConflictException(String message) {
        super(409, message);
    }

}
