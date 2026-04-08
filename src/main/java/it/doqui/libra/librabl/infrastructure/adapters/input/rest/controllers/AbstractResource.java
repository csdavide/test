package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.index.ecmengine.mtom.exception.MtomException;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.foundation.ErrorMessage;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.foundation.flow.BusinessContext;
import it.doqui.libra.librabl.foundation.flow.DispatchException;
import it.doqui.libra.librabl.infrastructure.platform.boot.Bootstrapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractResource {

    @ConfigProperty(name = "libra.logging.log-any-errors", defaultValue = "false")
    boolean logAnyErrors;

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    protected BusinessContext businessContext;

    @Inject
    protected SessionContext sessionContext;

    @Inject
    protected Bootstrapper bootstrapper;

    protected <T> List<String> flat(Collection<T> list) {
        return list == null || list.isEmpty()
            ? null
            : list.stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(s -> s.split(","))
            .flatMap(Arrays::stream)
            .toList();
    }

    protected void validate(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    protected <T> T validateAndGet(Supplier<T> validator) {
        try {
            return validator.get();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    protected Response call(Callable<Response> task) {
        try {
            return task.call();
        } catch (Throwable e) {
            return handleException(e);
        }
    }

    private Response handleException(Throwable e) {
        String method = null;
        if (e instanceof DispatchException exception) {
            if (exception.getMethod() != null) {
                method = StringUtils.stripToEmpty(exception.getClassName()) + "::" + exception.getMethod();
            }

            e = exception.getCause();
        } else {
            if (!businessContext.getStack().isEmpty()) {
                var invocationCall = businessContext.getStack().getFirst();
                if (invocationCall.methodName() != null) {
                    method = StringUtils.stripToEmpty(invocationCall.className()) + "::" + invocationCall.methodName();
                }
            }
        }

        if (e instanceof IllegalArgumentException exception) {
            e = new BadRequestException(exception.getMessage());
        } else if (e instanceof InvocationTargetException exception) {
            if (exception.getTargetException() != null) {
                e = exception.getTargetException();
            } else if (exception.getCause() != null) {
                e = exception.getCause();
            }
        } else if (e instanceof MtomException exception && exception.getCause() instanceof WebException we) {e = we;
            we.getDetailMap().computeIfAbsent("code", k -> "" + we.getCode());
            e = new WebException(500, we);
        } else if (e instanceof RuntimeException exception && exception.getCause() != null) {
            e = exception.getCause();
        }

        e = convert(e);

        final ErrorMessage errorMessage;
        if (e instanceof WebException we) {
            if (we.getErrorMessage() != null) {
                errorMessage = we.getErrorMessage();
            } else {
                errorMessage = new ErrorMessage(we.getCode(), e.getMessage());
            }

            errorMessage.getDetailMap().putAll(we.getDetailMap());
        } else {
            errorMessage = new ErrorMessage(500, e.getMessage());
        }

        var errorId = UuidCreator.getTimeOrderedEpoch().toString();
        errorMessage.getDetailMap().put("error", errorId);
        errorMessage.getDetailMap().put("method", method);

        var userContext = sessionContext.getUserContext();
        if (userContext != null) {
            errorMessage.getDetailMap().put("authority", userContext.getAuthorityRef().toString());
        }

        if (sessionContext != null) {
            errorMessage.getDetailMap().put("requestId", sessionContext.getOperationId());
            errorMessage.getDetailMap().put("application", sessionContext.getApplication());
            errorMessage.getDetailMap().put("userIdentity", sessionContext.getUserIdentity());
            errorMessage.getDetailMap().put("api", "" + sessionContext.getApiLevel());
            errorMessage.getDetailMap().put("channel", sessionContext.getChannel());

            if (sessionContext.getApiLevel() < 2) {
                var userInfo = new UserInfo();
                if (userContext != null) {
                    userInfo.setUsername(userContext.getAuthorityRef().getIdentity());
                    userInfo.setTenant(userContext.getAuthorityRef().getTenantRef().toString());
                }

                try {
                    errorMessage.getDetailMap().put("userInfo", objectMapper.writeValueAsString(userInfo));
                } catch (JsonProcessingException ex) {
                    log.warn("Got {} serializing {}", e.getMessage(), userInfo);
                }
            }
        }

        errorMessage.getDetailMap().put("host", bootstrapper.getHostName());
        if (errorMessage.getStatus() == 500 || logAnyErrors) {
            log.error("ERROR {}: {}; {}", errorId, e.getMessage(), errorMessage, e);
        }

        throw new WebException(errorMessage);
    }

    protected Throwable convert(Throwable e) {
        return e;
    }

    @Getter
    @Setter
    public static class UserInfo {
        private String username;
        private String tenant;
        private final String repository = "primary";
    }

}
