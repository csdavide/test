package it.doqui.libra.bridge;

import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;

@Getter
public class LibraRequest implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = -3042686055658047285L;

    private final String service;
    private final String method;
    private final ArrayList<Serializable> params;

    public LibraRequest(String service, String method) {
        this.service = service;
        this.method = method;
        this.params = new ArrayList<>();
    }
}
