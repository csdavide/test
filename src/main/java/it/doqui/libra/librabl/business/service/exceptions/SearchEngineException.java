package it.doqui.libra.librabl.business.service.exceptions;

public class SearchEngineException extends Exception {

    public SearchEngineException(String message) {
        super(message);
    }

    public SearchEngineException(Exception e) {
        super(e);
    }
}
