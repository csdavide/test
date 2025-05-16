package it.doqui.libra.librabl.api.v1.rest.exceptions;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractServiceException extends RuntimeException {

    protected String className;
    protected String methodName;
    protected String detail;

    public AbstractServiceException() {
	super();
    }

    public AbstractServiceException(String message) {
	super(message);
    }

    public AbstractServiceException(String message, Throwable cause) {
	super(message, cause);
    }

    public abstract int getStatus();
}