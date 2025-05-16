package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.views.acl.EditableSecurityGroup;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import it.doqui.libra.librabl.views.acl.SecurityGroupItem;

import java.util.Collection;
import java.util.Optional;

public interface SecurityGroupService {
    Paged<SecurityGroupItem> find(String namePrefix, boolean readable, Pageable pageable);
    Optional<SecurityGroupItem> findByUUID(String sgid, boolean readable);
    SecurityGroupItem create(EditableSecurityGroup sg, boolean readable);
    void addPermissions(String sgid, final Collection<PermissionItem> permissions);
    void update(String sgid, EditableSecurityGroup sg);
    void rename(String sgid, String name);
}
