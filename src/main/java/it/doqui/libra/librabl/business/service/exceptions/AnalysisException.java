package it.doqui.libra.librabl.business.service.exceptions;

public class AnalysisException extends Exception {

    public AnalysisException() {
        super();
    }

    public AnalysisException(String message) {
        super(message);
    }

    public AnalysisException(Exception e) {
        super(e);
    }

    public AnalysisException(String message, Exception e) {
        super(message, e);
    }
}
