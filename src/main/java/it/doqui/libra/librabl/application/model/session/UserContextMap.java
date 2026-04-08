package it.doqui.libra.librabl.application.model.session;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@RequestScoped
@Getter
public class UserContextMap {
    private final Map<String, Object> map = new HashMap<>();
}
