package it.doqui.libra.librabl.business.provider.security;

import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import org.apache.commons.lang3.StringUtils;

public class AuthorityUtils {

    private AuthorityUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String normalizeAuthority(String key, TenantRef tenantRef) {
        String r = null;
        if (key != null) {
            if (key.contains("@")) {
                var authorityRef = AuthorityRef.valueOf(key);
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
