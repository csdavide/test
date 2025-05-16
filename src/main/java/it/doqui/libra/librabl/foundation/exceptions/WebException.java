package it.doqui.libra.librabl.foundation.exceptions;

import it.doqui.libra.librabl.foundation.ErrorMessage;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class WebException extends RuntimeException {

    private final int code;
    private final Map<String,String> detailMap;
    private final ErrorMessage errorMessage;

    public WebException() {
        this(500, "Server Error");
    }

    public WebException(String message) {
        this(500, message);
    }

    public WebException(int code) {
        super();
        this.code = code;
        this.errorMessage = null;
        this.detailMap = new HashMap<>();
    }

    public WebException(int code, String message) {
        super(message);
        this.code = code;
        this.errorMessage = null;
        this.detailMap = new HashMap<>();
    }

    public WebException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.errorMessage = null;
        this.detailMap = new HashMap<>();
    }

    public WebException(ErrorMessage errorMessage) {
        super(errorMessage.getMessage());
        this.code = errorMessage.getStatus();
        this.errorMessage = errorMessage;
        this.detailMap = new HashMap<>();
    }

    public WebException(int code, WebException we) {
        super(we.getMessage());
        this.code = code;
        this.errorMessage = we.errorMessage;
        this.detailMap = we.detailMap;
    }
}
