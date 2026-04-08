package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.application.model.acl.EditableSecurityGroup;
import it.doqui.libra.librabl.application.model.acl.PermissionItem;
import it.doqui.libra.librabl.application.model.acl.SecurityGroupItem;

import java.util.Collection;
import java.util.Optional;

public interface SecurityGroupUseCase {
    Paged<SecurityGroupItem> find(String namePrefix, boolean readable, Pageable pageable);
    Optional<SecurityGroupItem> findByUUID(String sgid, boolean readable);
    SecurityGroupItem create(EditableSecurityGroup sg, boolean readable);
    void addPermissions(String sgid, final Collection<PermissionItem> permissions);
    void update(String sgid, EditableSecurityGroup sg);
    void rename(String sgid, String name);
}
