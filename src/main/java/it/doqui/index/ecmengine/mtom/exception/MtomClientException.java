package it.doqui.index.ecmengine.mtom.exception;

public class MtomClientException extends Exception {
    private static final long serialVersionUID = 4971463315248717927L;

    public MtomClientException(String message) {
	super(message);
    }

    /**
     * Costruttore che prende in input il messaggio dell'eccezione e la sua causa.
     */
    public MtomClientException(String msg, Throwable cause) {
	super(msg, cause);
	setType(cause);
    }

    private TYPE type;

    public TYPE getType() {
	return type;
    }

    public enum TYPE {
	BAD_REQUEST, SYS_ERROR
    }

    private void setType(Throwable cause) {
	if (cause instanceof GroupAlreadyExistsException || cause instanceof InvalidCredentialsException
		|| cause instanceof InvalidParameterException || cause instanceof NoDataExtractedException
		|| cause instanceof NoSuchGroupException || cause instanceof NoSuchNodeException
		|| cause instanceof NoSuchUserException || cause instanceof PermissionDeniedException
		|| cause instanceof TooManyResultsException || cause instanceof UserAlreadyExistsException) {
	    type = TYPE.BAD_REQUEST;
	} else {
	    type = TYPE.SYS_ERROR;
	}
    }
}
