package it.doqui.libra.librabl.application.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.doqui.libra.librabl.application.ports.in.SecurityGroupUseCase;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.AclDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.SecurityGroup;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.acl.EditableSecurityGroup;
import it.doqui.libra.librabl.application.model.acl.PermissionItem;
import it.doqui.libra.librabl.application.model.acl.SecurityGroupItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class SecurityGroupManager implements SecurityGroupUseCase {

    @Inject
    AclDAO aclDAO;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Paged<SecurityGroupItem> find(String namePrefix, boolean readable, Pageable pageable) {
        return aclDAO
            .findSecurityGroups(namePrefix, true, pageable)
            .map(sg -> map(sg, readable));
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Optional<SecurityGroupItem> findByUUID(String sgid, boolean readable) {
        try {
            var sg = findUnmanagedSG(sgid, true);
            return Optional.of(map(sg, readable));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public SecurityGroupItem create(EditableSecurityGroup sg, boolean readable) {
        return transactionManagerPort.perform(tx -> {
            var createdSG = aclDAO.createUnmanagedSG(tx, sg);
            return PerformResult.<SecurityGroupItem>builder()
                .result(map(createdSG, readable))
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void addPermissions(String sgid, final Collection<PermissionItem> permissions) {
        transactionManagerPort.perform(tx -> {
            var sg = findUnmanagedSG(sgid, false);
            aclDAO.addAccessRules(sg.getId(), sessionContext.getUserContext().getTenantRef(), permissions);

            sg.setTx(tx);
            aclDAO.setTx(sg.getId(), tx.getId());
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void update(String sgid, EditableSecurityGroup esg) {
        transactionManagerPort.perform(tx -> {
            var sg = findUnmanagedSG(sgid, false);
            sg.setName(ObjectUtils.getIfDefined(esg.getName(), sg.getName(), false));
            aclDAO.removeAllSecurityGroupRules(sg.getId());
            aclDAO.addAccessRules(sg.getId(), sessionContext.getUserContext().getTenantRef(), esg.getPermissions());

            Optional
                .ofNullable(esg.getName()).flatMap(x -> x)
                .ifPresent(name -> aclDAO.renameSecurityGroupWhereId(sg.getId(), name));

            aclDAO.setTx(sg.getId(), tx.getId());
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void rename(String sgid, String name) {
        QuarkusTransaction.joiningExisting().call(() -> {
            var s = findUnmanagedSG(sgid, false);
            aclDAO.renameSecurityGroupWhereId(s.getId(), name);
            return null;
        });
    }

    private SecurityGroupItem map(SecurityGroup sg, boolean readable) {
        SecurityGroupItem r = new SecurityGroupItem();
        r.setSgID(sg.getUuid());
        if (sg.getName() != null) {
            r.setName(Optional.of(sg.getName()));
        }

        r.getPermissions().addAll(
            sg.getRules()
                .stream()
                .map(rule -> {
                    var p = new PermissionItem();
                    p.setAuthority(rule.getAuthority());
                    p.setRights(toPermission(readable, rule.getRights()));
                    return p;
                })
                .toList()
        );

        return r;
    }

    private String toPermission(boolean readable, String rights) {
        if (!readable) {
            return PermissionFlag.formatAsBinary(PermissionFlag.parse(rights));
        }

        return PermissionFlag.formatAsHumanReadable(PermissionFlag.parse(rights));
    }

    private SecurityGroup findUnmanagedSG(String sgid, boolean includeRules) {
        var sg = aclDAO.findSecurityGroup(sgid, includeRules)
            .orElseThrow(() -> new NotFoundException("Unable to find unmanaged SG " + sgid));

        if (sg.isManaged()) {
            throw new ForbiddenException("Forbidden operation on managed SG");
        }

        return sg;
    }
}
