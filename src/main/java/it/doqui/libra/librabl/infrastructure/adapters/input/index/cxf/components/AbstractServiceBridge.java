package it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components;

import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.platform.boot.Bootstrapper;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
public abstract class AbstractServiceBridge {

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Inject
    SessionContext sessionContext;

    @Inject
    Bootstrapper bootstrapper;

    protected static <T> T requireNonNull(T obj, String name) {
        return Objects.requireNonNull(obj, String.format("%s must not be null", name));
    }

    protected void handleContext(MtomOperationContext context) {
        try {
            requireNonNull(context, "Operation");
            requireNonNull(context.getUsername(), "Username");
            requireNonNull(context.getPassword(), "Password");
        } catch (NullPointerException e) {
            throw new InvalidParameterException(e.getMessage());
        }

        String[] u = context.getUsername().split("@", 2);
        AuthorityRef authorityRef = new AuthorityRef(u[0], new TenantRef(u.length > 1 ? u[1]: null));

        try {
            authenticationManagerPort.authenticateUser(authorityRef, Optional.ofNullable(context.getPassword()), SessionMode.SYNC);
            sessionContext.setApplication(context.getFruitore());
            sessionContext.setUserIdentity(context.getNomeFisico());
            sessionContext.setChannel(UserContext.CHANNEL_CXF);
        } catch (UnauthorizedException e) {
            throw new InvalidCredentialsException();
        }
    }

    protected <R> R call(MtomOperationContext context, Callable<R> task) {
        try {
            if (context != null) {
                handleContext(context);
            }

            sessionContext.setMode(SessionMode.SYNC);
            sessionContext.setApiLevel(1);

            return task.call();
        } catch (UnauthorizedException e) {
            throw new InvalidCredentialsException(e.getMessage());
        } catch (ForbiddenException | LockedException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (SearchEngineException | BadQueryException e) {
            throw new SearchException(e.getMessage());
        } catch (BadRequestException | IllegalArgumentException e) {
            log.error(e.getMessage(), e);
            throw new InvalidParameterException(e.getMessage());
        } catch (NotFoundException e) {
            throw new NoSuchNodeException(e.getMessage());
        } catch (EcmEngineTransactionException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (MtomException e) {
            throw e;
        } catch (Throwable e) {
            throw logAndWrap(e);
        }
    }

    protected <R> R call(Callable<R> task) {
        authenticationManagerPort.loginAsGuest();
        sessionContext.setChannel(UserContext.CHANNEL_CXF);
        return call(null, task);
    }

    protected void validate(Runnable task) {
        try {
            task.run();
        } catch (IllegalArgumentException | BadRequestException | NullPointerException e) {
            throw new InvalidParameterException(e.getMessage());
        } catch (MtomException e) {
            throw e;
        } catch (Throwable e) {
            throw logAndWrap(e);
        }
    }

    private EcmEngineException logAndWrap(Throwable e) {
        var hostname = bootstrapper.getHostName();
        var errmsg = String.format("ERROR %s (%s)", UuidCreator.getTimeOrderedEpoch(), hostname);
        log.error(errmsg + ": " + e.getMessage(), e);
        return new EcmEngineException(errmsg);
    }

    protected void validate(Node node) {
        validate(node, null);
    }

    protected void validate(Node node, String name) {
        validate(() -> {
            Objects.requireNonNull(node, String.format("Node %s must not be null", StringUtils.isBlank(name) ? "" : " '" + name + "'"));
            Objects.requireNonNull(StringUtils.stripToNull(node.getUid()), String.format("Node '%s' UUID must not be null", StringUtils.isBlank(name) ? "" : " '" + name + "'"));
        });
    }

    protected void validate(Node[] nodes) {
        validate(() -> {
            requireNonNull(nodes, "Nodes");

            for (Node node : nodes) {
                requireNonNull(StringUtils.stripToNull(node.getUid()), "Node uid");
            }
        });
    }

    protected LinkItemRequest asLink(String vertex, Association association, boolean hard) {
        var link = new LinkItemRequest();
        link.setVertexUUID(vertex);
        link.setRelationship(association.isChildAssociation() ? RelationshipKind.PARENT : RelationshipKind.SOURCE);
        link.setHard(hard);
        link.setTypeName(association.getTypePrefixedName());
        link.setName(association.getPrefixedName());
        return link;
    }

    protected FileFormatInfo[] incorrect(FileFormatInfo[] ffi) {
        for (var fi : ffi) {
            if (Strings.CS.contains(fi.getTypeDescription(), "positive")) {
                fi.setTypeDescription(fi.getTypeDescription().replace("positive", "posistive"));
            }
        }
        return ffi;
    }

    protected FileReport incorrect(FileReport fr) {
        for (var fi : fr.getFormats()) {
            if (Strings.CS.contains(fi.getTypeDescription(), "positive")) {
                fi.setTypeDescription(fi.getTypeDescription().replace("positive", "posistive"));
            }
        }
        return fr;
    }
}
