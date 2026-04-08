package it.doqui.libra.librabl.foundation.exceptions;

public class LimitExceededException extends WebException {

    public LimitExceededException() {
        super(422);
    }

    public LimitExceededException(String message) {
        super(422, message);
    }
}
