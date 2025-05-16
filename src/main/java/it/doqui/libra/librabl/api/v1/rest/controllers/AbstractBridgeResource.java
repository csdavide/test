package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.foundation.flow.BusinessDispatcher;
import it.doqui.libra.librabl.utils.IOUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

public abstract class AbstractBridgeResource extends AbstractResource {

    @Inject
    protected BusinessDispatcher dispatcher;

    public Response call(String tenant, Callable<Response> task) {
        return super.call(() -> {
            if (!StringUtils.equalsIgnoreCase(tenant, UserContextManager.getTenant())) {
                throw new ForbiddenException("Tenant does not match");
            }

            return task.call();
        });
    }

    @Override
    protected Throwable convert(Throwable e) {
        e = super.convert(e);
        if (e instanceof NoSuchNodeException) {
            e = new NotFoundException(e);
        } else if (e instanceof InvalidCredentialsException) {
            e = new UnauthorizedException(e.getMessage());
        } else if (e instanceof PermissionDeniedException) {
            e = new ForbiddenException(e.getMessage());
        } else if (e instanceof InvalidParameterException) {
            e = new BadRequestException(e.getMessage());
        } else if (e instanceof UserAlreadyExistsException) {
            e = new BadRequestException(e.getMessage());
        } else if (e instanceof GroupAlreadyExistsException) {
            e = new BadRequestException(e.getMessage());
        }

        return e;
    }

    protected InputPart getInputPart(String param, MultipartFormDataInput input, boolean required) throws BadRequestException {
        var formDataMap = input.getFormDataMap();
        var inputParts = formDataMap.get(param);
        if (inputParts != null && !inputParts.isEmpty()) {
            if (inputParts.size() > 1) {
                throw new BadRequestException(String.format("Wrong '%s' parts number: Expected 1, actual %d", param, inputParts.size()));
            }

            return inputParts.get(0);
        } else if (required) {
            throw new BadRequestException(String.format("Missing '%s' part", param));
        } else {
            return null;
        }
    }

    protected String getStringFromMultipart(String param, MultipartFormDataInput input, boolean required) throws BadRequestException {
        return Optional.ofNullable(getInputPart(param, input, required)).map(p -> {
            try {
                return p.getBodyAsString();
            } catch (IOException e) {
                throw new SystemException(e);
            }
        }).orElse(null);
    }

    protected <T> T getObjectFromMultipart(String param, MultipartFormDataInput input, boolean required, Class<T> targetClass) {
        var part = getInputPart(param, input, required);
        if (part == null) {
            return null;
        }

        try {
            return part.getBody(targetClass, null);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    protected InputStream getInputStreamFromMultipart(String param, MultipartFormDataInput input) throws BadRequestException, IOException {
        return Optional.ofNullable(getInputPart(param, input, true).getBody(InputStream.class, null))
            .orElseThrow(() -> new BadRequestException("Null InputStream"));
    }

    protected byte[] getByteArraysFromMultipart(String param, MultipartFormDataInput input, boolean required) throws BadRequestException {
        return Optional.ofNullable(getInputPart(param, input, required))
            .map(p -> {
                try {
                    return Optional.ofNullable(p.getBody(InputStream.class, null))
                        .map(is -> {
                            try {
                                return IOUtils.readFully(is);
                            } catch (IOException e) {
                                throw new SystemException(e);
                            }
                        })
                        .orElseThrow(() -> new BadRequestException("Null InputStream"));
                } catch (IOException e) {
                    throw new SystemException("IOException during InputStream body conversion: " + e.getMessage(), e);
                }
            })
            .orElse(null);
    }

    protected Map<Integer, byte[]> getMultipleByteArraysFromMultipart(String baseParam, MultipartFormDataInput input, int maxSize) throws BadRequestException {
        var mapResult = new HashMap<Integer, byte[]>();
        for (int i = 1; i <= maxSize; i++) {
            byte[] bytes = getByteArraysFromMultipart(baseParam + "_" + i, input, false);
            mapResult.put(i - 1, bytes);
        }

        return mapResult;
    }
}
