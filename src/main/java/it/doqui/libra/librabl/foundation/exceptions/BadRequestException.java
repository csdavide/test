package it.doqui.libra.librabl.foundation.exceptions;

public class BadRequestException extends WebException {

    public BadRequestException() {
        super(400);
    }

    public BadRequestException(String message) {
        super(400, message);
    }
}
