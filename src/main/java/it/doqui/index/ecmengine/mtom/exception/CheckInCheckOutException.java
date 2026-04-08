package it.doqui.index.ecmengine.mtom.exception;

public class CheckInCheckOutException extends MtomException {

    private static final long serialVersionUID = 3608030971317914058L;

    /**
     * Costruttore che prende in input il messaggio dell'eccezione.
     */
    public CheckInCheckOutException(String msg) {
	super(msg);
    }
}
