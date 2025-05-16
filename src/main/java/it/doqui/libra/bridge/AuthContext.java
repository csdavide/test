package it.doqui.libra.bridge;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class AuthContext implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = -3042686055658047285L;

    private String username;
    private String password;
    private String nomeFisico;
    private String fruitore;
    private String repository;
}
