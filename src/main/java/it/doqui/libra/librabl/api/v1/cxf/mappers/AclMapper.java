package it.doqui.libra.librabl.api.v1.cxf.mappers;

import it.doqui.index.ecmengine.mtom.dto.AclRecord;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class AclMapper {

    public List<PermissionItem> asList(AclRecord[] aclRecords) {
        if (aclRecords == null) {
            return null;
        }
        TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        List<PermissionItem> result = new ArrayList<>(aclRecords.length);
        for (AclRecord aclRecord : aclRecords) {
            String rights = mapAsRights(aclRecord.getPermission());
            if (rights != null) {
                PermissionItem p = new PermissionItem();
                p.setAuthority(normalizeAuthority(aclRecord.getAuthority(), tenantRef));
                p.setRights(rights);
                result.add(p);
            } else {
                throw new BadDataException("Missing or invalid rights for authority " + aclRecord.getAuthority());
            }
        }
        return result;
    }

    public AclRecord map(PermissionItem permission) {
        AclRecord r = new AclRecord();
        r.setAuthority(permission.getAuthority());
        r.setPermission(mapAsRole(permission.getRights()));
        r.setAccessAllowed(true);
        return r;
    }

    public AclRecord map(Map.Entry<String,String> entry) {
        String role = mapAsRole(entry.getValue());
        if (role == null) {
            return null;
        }

        AclRecord r = new AclRecord();
        r.setAuthority(entry.getKey());
        r.setPermission(role);
        r.setAccessAllowed(true);
        return r;
    }

    public String mapAsAuthority(String authority) {
        return normalizeAuthority(authority, UserContextManager.getContext().getTenantRef());
    }

    public String mapAsRole(String rights) {
        rights = StringUtils.stripToEmpty(rights);
        if (rights.length() < 5) {
            StringBuilder rightsBuilder = new StringBuilder(rights);
            for (int i = rightsBuilder.length(); i < 5; i++) {
                rightsBuilder.append("0");
            }
            rights = rightsBuilder.toString();
        }

        if (!rights.isEmpty()) {
            boolean all = true;
            for (int i = 0; i < rights.length(); i++) {
                if (rights.charAt(i) != '1') {
                    all = false;
                    break;
                }
            }

            if (all) {
                return "All";
            }
        }

        return switch (rights.substring(0, 5)) {
            case "00000" -> null;
            case "10000" -> "Consumer";
            case "11000" -> "Editor";
            case "10100" -> "Contributor";
            case "11100" -> "Collaborator";
            case "11110" -> "Coordinator";
            case "11111" -> "Admin";
            default -> PermissionFlag.formatAsHumanReadable(PermissionFlag.parse(rights));
        };
    }

    public String mapAsRights(String role) {
        return switch (StringUtils.stripToEmpty(role).toLowerCase()) {
            case "consumer", "read" -> "10000";
            case "editor" -> "11000";
            case "contributor" -> "10100";
            case "collaborator" -> "11100";
            case "coordinator" -> "11110";
            case "admin", "all" -> "11111";
            default -> null;
        };
    }

    private String normalizeAuthority(String key, TenantRef tenantRef) {
        String r = null;
        if (key != null) {
            if (key.contains("@")) {
                AuthorityRef authorityRef = AuthorityRef.valueOf(key);
                if (!StringUtils.equalsIgnoreCase(authorityRef.getTenantRef().getName(), tenantRef.getName())) {
                    throw new BadDataException("Tenant does not match: " + authorityRef.getTenantRef().getName());
                }

                r = new AuthorityRef(authorityRef.getIdentity(), tenantRef).toString();
            } else {
                r = key.toUpperCase();
                if (StringUtils.startsWith(r, "GROUP_") && !StringUtils.equals(r, "GROUP_EVERYONE")) {
                    r = r.substring(6);
                }
            }
        }
        return r;
    }
}
