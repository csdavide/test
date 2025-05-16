package it.doqui.libra.librabl.foundation.exceptions;

public class PreconditionFailedException extends WebException {

    public PreconditionFailedException() {
        super(412);
    }

    public PreconditionFailedException(String message) {
        super(412, message);
    }

}
