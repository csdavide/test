package it.doqui.libra.librabl.foundation.exceptions;

public class NotFoundException extends WebException {
    public NotFoundException() {
        super(404);
    }

    public NotFoundException(String message) {
        super(404, message);
    }

    public NotFoundException(Throwable e) {
        super(404, e.getMessage(), e);
    }

}
