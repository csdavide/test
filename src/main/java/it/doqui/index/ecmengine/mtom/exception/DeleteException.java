package it.doqui.index.ecmengine.mtom.exception;

public class DeleteException extends MtomException {

    private static final long serialVersionUID = 4802825301887590279L;

    /**
     * Costruttore che prende in input il messaggio dell'eccezione.
     */
    public DeleteException(String msg) {
	super(msg);
    }
}
