package it.doqui.libra.librabl.business.provider.core;

import it.doqui.libra.librabl.foundation.ErrorMessage;
import it.doqui.libra.librabl.foundation.exceptions.WebException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Optional;

@Provider
public class MyExceptionHandler implements ExceptionMapper<WebException> {

    @Override
    public Response toResponse(WebException e) {
        return Response.status(e.getCode(), e.getMessage())
            .entity(Optional.ofNullable(e.getErrorMessage()).orElse(new ErrorMessage(e.getCode(), e.getMessage())))
            .header("Content-Type", "application/json")
            .build();
    }
}
