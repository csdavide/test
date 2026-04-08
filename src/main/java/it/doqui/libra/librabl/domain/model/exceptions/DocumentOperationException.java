package it.doqui.libra.librabl.domain.model.exceptions;

public class DocumentOperationException extends Exception {
    public DocumentOperationException() {
    }

    public DocumentOperationException(String message) {
        super(message);
    }

    public DocumentOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentOperationException(Throwable cause) {
        super(cause);
    }
}
