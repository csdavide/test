package it.doqui.libra.librabl.domain.model.exceptions;

public class SearchEngineException extends Exception {

    public SearchEngineException(String message) {
        super(message);
    }

    public SearchEngineException(Exception e) {
        super(e);
    }
}
