package it.doqui.index.ecmengine.mtom.exception;

public class MtomException extends RuntimeException {

    public MtomException(String message) {
	super(message);
    }

    /**
     * Costruttore che prende in input il messaggio dell'eccezione e la sua causa.
     */
    public MtomException(String msg, Throwable cause) {
	super(msg, cause);
    }

    private static final long serialVersionUID = -3012127249200991844L;
}
