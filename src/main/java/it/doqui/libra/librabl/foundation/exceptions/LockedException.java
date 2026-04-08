package it.doqui.libra.librabl.foundation.exceptions;

public class LockedException extends WebException {
    public LockedException() {
        super(423);
    }

    public LockedException(String message) {
        super(423, message);
    }
}
