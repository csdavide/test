package it.doqui.libra.librabl.foundation.exceptions;

public class ForbiddenException extends WebException {
    public ForbiddenException() {
        super(403);
    }

    public ForbiddenException(String message) {
        super(403, message);
    }
}
