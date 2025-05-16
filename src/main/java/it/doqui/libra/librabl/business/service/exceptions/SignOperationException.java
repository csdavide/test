package it.doqui.libra.librabl.business.service.exceptions;

public class SignOperationException extends Exception {
    public SignOperationException() {
    }

    public SignOperationException(String message) {
        super(message);
    }

    public SignOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SignOperationException(Throwable cause) {
        super(cause);
    }
}
