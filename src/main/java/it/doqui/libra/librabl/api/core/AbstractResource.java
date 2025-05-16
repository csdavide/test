package it.doqui.libra.librabl.api.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.index.ecmengine.mtom.exception.MtomException;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.ErrorMessage;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.foundation.flow.BusinessContext;
import it.doqui.libra.librabl.foundation.flow.DispatchException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractResource {

    @ConfigProperty(name = "libra.logging.log-any-errors", defaultValue = "false")
    boolean logAnyErrors;

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    BusinessContext businessContext;

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

        var errorId = UUID.randomUUID().toString();
        errorMessage.getDetailMap().put("error", errorId);
        errorMessage.getDetailMap().put("method", method);

        var ctx = UserContextManager.getContext();
        if (ctx != null) {
            errorMessage.getDetailMap().put("requestId", ctx.getOperationId());
            errorMessage.getDetailMap().put("application", ctx.getApplication());
            errorMessage.getDetailMap().put("userIdentity", ctx.getUserIdentity());
            errorMessage.getDetailMap().put("api", "" + ctx.getApiLevel());
            errorMessage.getDetailMap().put("channel", ctx.getChannel());

            var authorityRef = ctx.getAuthorityRef();
            errorMessage.getDetailMap().put("authority", authorityRef.toString());

            if (ctx.getApiLevel() < 2) {
                var userInfo = new UserInfo();
                userInfo.setUsername(authorityRef.getIdentity());
                userInfo.setTenant(authorityRef.getTenantRef().toString());

                try {
                    errorMessage.getDetailMap().put("userInfo", objectMapper.writeValueAsString(userInfo));
                } catch (JsonProcessingException ex) {
                    log.warn("Got {} serializing {}", e.getMessage(), userInfo);
                }
            }
        }

        try {
            var hostname = InetAddress.getLocalHost().getHostName();
            errorMessage.getDetailMap().put("host", hostname);
        } catch (UnknownHostException ex) {
           // ignore
        }

        if (errorMessage.getStatus() == 500 || logAnyErrors) {
            log.error(String.format("ERROR %s: %s; %s", errorId, e.getMessage(), errorMessage), e);
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
