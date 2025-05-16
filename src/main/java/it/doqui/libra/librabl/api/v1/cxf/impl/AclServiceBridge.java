package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.AclListParams;
import it.doqui.index.ecmengine.mtom.dto.AclRecord;
import it.doqui.index.ecmengine.mtom.dto.MtomOperationContext;
import it.doqui.index.ecmengine.mtom.dto.Node;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.cxf.mappers.AclMapper;
import it.doqui.libra.librabl.business.service.interfaces.PermissionService;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class AclServiceBridge extends AbstractServiceBridge {

    @Inject
    PermissionService permissionService;

    @Inject
    AclMapper aclMapper;

    protected void validate(AclRecord[] acls, boolean checkAllowed) {
        validate(() -> {
            Objects.requireNonNull(acls, "ACLs must not be null");
            for (AclRecord acl : acls) {
                Objects.requireNonNull(acl, "ACL record must not be null");
                Objects.requireNonNull(StringUtils.stripToNull(acl.getAuthority()), "ACL authority is either null or empty");
                Objects.requireNonNull(StringUtils.stripToNull(acl.getPermission()), "ACL permission is either null or empty");
                if(checkAllowed && !acl.isAccessAllowed()) {
                    throw new InvalidParameterException("ACL access allowed must be true.");
                }
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public void addAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws AclEditException, NoSuchNodeException, InvalidParameterException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {

        validate(node);
        validate(acls, true);
        call(context, () -> {
            try {
                permissionService.addPermissions(node.getUid(), aclMapper.asList(acls));
                return null;
            } catch (BadDataException e) {
                throw new AclEditException(node.getUid());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void changeAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, AclEditException, NoSuchNodeException,
        EcmEngineTransactionException, PermissionDeniedException {
        validate(node);
        call(context, () -> {
            try {
                permissionService.replacePermissions(node.getUid(), aclMapper.asList(acls), Optional.empty());
                return null;
            } catch (BadDataException e) {
                throw new AclEditException(node.getUid());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void updateAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws AclEditException, NoSuchNodeException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {
        validate(node);
        validate(acls, true);
        call(context, () -> {
            try {
                permissionService.replacePermissions(node.getUid(), aclMapper.asList(acls), Optional.of(true));
                return null;
            } catch (BadDataException e) {
                throw new AclEditException(node.getUid());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void removeAcl(Node node, AclRecord[] acls, MtomOperationContext context)
        throws AclEditException, NoSuchNodeException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {

        validate(node);
        validate(acls, false);

        call(context, () -> {
            try {
                permissionService.removePermissions(node.getUid(), aclMapper.asList(acls));
                return null;
            } catch (BadDataException e) {
                throw new AclEditException(node.getUid());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void resetAcl(Node node, AclRecord filter, MtomOperationContext context)
        throws InvalidParameterException, AclEditException, NoSuchNodeException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {

        validate(node);

        call(context, () -> {
            String authority = null;
            String permission = null;
            if (filter != null) {
                authority = aclMapper.mapAsAuthority(filter.getAuthority());
                if (filter.getPermission() != null) {
                    permission = aclMapper.mapAsRights(filter.getPermission());
                }
            }

            if (StringUtils.isNotBlank(authority)) {
                if (StringUtils.isNotBlank(permission)) {
                    var p = new PermissionItem();
                    p.setAuthority(authority);
                    p.setRights(aclMapper.mapAsRights(permission));
                    permissionService.removePermissions(node.getUid(), List.of(p));
                } else {
                    permissionService.removeAllAuthorityPermissions(node.getUid(), authority);
                }
            } else {
                permissionService.replacePermissions(node.getUid(), List.of(), Optional.of(true));
            }

            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void setInheritsAcl(Node node, boolean inherits, MtomOperationContext context)
        throws NoSuchNodeException, AclEditException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {

        validate(node);
        call(context, () -> {
            permissionService.setInheritance(node.getUid(), inherits);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public boolean isInheritsAcl(Node node, MtomOperationContext context) throws NoSuchNodeException,
        AclEditException, InvalidParameterException, EcmEngineTransactionException, InvalidCredentialsException {

        validate(node);
        return call(context, () -> permissionService.isInheritance(node.getUid()));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public AclRecord[] listAcl(Node node, AclListParams params, MtomOperationContext context)
        throws NoSuchNodeException, AclEditException, InvalidParameterException,
        InvalidCredentialsException, EcmEngineTransactionException, PermissionDeniedException {

        validate(node);
        return call(context, () -> {
            var kind = params != null && params.isShowInherited()
                ? PermissionService.PermissionKind.ALL
                : PermissionService.PermissionKind.NODE;
            var pd = permissionService.listPermissions(node.getUid(), kind, false);
            return pd.getPermissions()
                .stream()
                .map(aclMapper::map)
                .toList()
                .toArray(new AclRecord[0]);
        });
    }
}
