package it.doqui.libra.librabl.foundation.exceptions;

public class UnauthorizedException extends WebException {

    public UnauthorizedException() {
        super(401);
    }

    public UnauthorizedException(String message) {
        super(401, message);
    }
}
