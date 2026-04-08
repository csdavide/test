package it.doqui.libra.librabl.foundation;

import lombok.Getter;

@Getter
public class AuthorityRef {
    private final String identity;
    private final TenantRef tenantRef;

    public AuthorityRef(String identity, TenantRef tenantRef) {
        this.identity = identity;
        this.tenantRef = tenantRef;
    }

    public static AuthorityRef valueOf(String s) {
        String[] x = s.split("@");
        return new AuthorityRef(x[0], x.length > 1 ? TenantRef.valueOf(x[1]) : new TenantRef());
    }

    @Override
    public String toString() {
        return String.format("%s@%s", identity, tenantRef.getName());
    }
}
