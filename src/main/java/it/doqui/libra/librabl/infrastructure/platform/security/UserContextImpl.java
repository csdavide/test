package it.doqui.libra.librabl.infrastructure.platform.security;

import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.security.Principal;
import java.util.*;

@Getter
@Setter
@ToString
public final class UserContextImpl implements UserContext {

    private AuthorityRef authorityRef;
    private String dbSchema;

    private final Map<String,Object> attributes;

    private final Set<String> roleSet;
    private final Set<String> scopeSet;

    @Setter(AccessLevel.PACKAGE)
    private String authenticationScheme;

    @Setter(AccessLevel.PACKAGE)
    private boolean secure;

    @Setter
    private ModelSchema schema;

    private final Set<String> groupSet;

    private final Set<Long> securityGroupSet;

    @Override
    public Set<String> getRoleSet() {
        return Set.copyOf(roleSet);
    }

    @Override
    public Set<String> getScopeSet() {
        return Set.copyOf(scopeSet);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Set<String> getGroupSet() {
        return Set.copyOf(groupSet);
    }

    void addGroup(String group) {
        this.groupSet.add(group);
    }

    void addScope(String scope) {
        this.scopeSet.add(scope);
    }

    void addRole(String role) { this.roleSet.add(role); }

    public UserContextImpl() {
        roleSet = new HashSet<>();
        scopeSet = new HashSet<>();
        groupSet = new HashSet<>();
        groupSet.add(GROUP_EVERYONE);

        securityGroupSet = new HashSet<>();
        attributes  = new HashMap<>();
    }

    public UserContext copy() {
        UserContextImpl copy = new UserContextImpl();
        copy.authorityRef = this.authorityRef;
        copy.dbSchema = this.dbSchema;
        copy.roleSet.addAll(this.roleSet);
        copy.scopeSet.addAll(this.scopeSet);
        copy.groupSet.addAll(this.groupSet);
        copy.attributes.putAll(this.attributes);
        copy.securityGroupSet.addAll(this.securityGroupSet);

        return copy;
    }

    @Override
    public Principal getUserPrincipal() {
        return () -> Optional.ofNullable(authorityRef).map(AuthorityRef::getIdentity).orElse(null);
    }

    @Override
    public boolean isUserInRole(String role) {
        return roleSet.contains(role);
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }
}

