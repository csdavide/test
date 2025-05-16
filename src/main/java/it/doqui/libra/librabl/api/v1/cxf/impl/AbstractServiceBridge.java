package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.business.service.auth.AuthenticationService;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.exceptions.SearchEngineException;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

@Slf4j
public abstract class AbstractServiceBridge {

    @Inject
    AuthenticationService authenticationService;

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
            UserContext userContext = authenticationService.authenticateUser(authorityRef, Optional.ofNullable(context.getPassword()), UserContext.Mode.SYNC);
            userContext.setChannel(UserContext.CHANNEL_CXF);
            userContext.setApiLevel(1);
            userContext.setApplication(context.getFruitore());
            userContext.setUserIdentity(context.getNomeFisico());
        } catch (UnauthorizedException e) {
            throw new InvalidCredentialsException();
        }

    }

    protected <R> R call(MtomOperationContext context, Callable<R> task) {
        try {
            if (context != null) {
                handleContext(context);
            }

            return task.call();
        } catch (UnauthorizedException e) {
            throw new InvalidCredentialsException(e.getMessage());
        } catch (ForbiddenException e) {
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
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            hostname = "unknown host";
        }
        var errmsg = String.format("ERROR %s (%s)", UUID.randomUUID(), hostname);
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
            if (StringUtils.contains(fi.getTypeDescription(), "positive")) {
                fi.setTypeDescription(fi.getTypeDescription().replace("positive", "posistive"));
            }
        }
        return ffi;
    }

    protected FileReport incorrect(FileReport fr) {
        for (var fi : fr.getFormats()) {
            if (StringUtils.contains(fi.getTypeDescription(), "positive")) {
                fi.setTypeDescription(fi.getTypeDescription().replace("positive", "posistive"));
            }
        }
        return fr;
    }
}
