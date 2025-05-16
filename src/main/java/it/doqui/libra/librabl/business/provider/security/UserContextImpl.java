package it.doqui.libra.librabl.business.provider.security;

import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
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
    private String channel;
    private String application;
    private String userIdentity;
    private String operationId;
    private int apiLevel;

    private final Map<String,Object> attributes;

    private final Set<String> roleSet;
    private final Set<String> scopeSet;

    @Setter(AccessLevel.PACKAGE)
    private String authenticationScheme;

    @Setter(AccessLevel.PACKAGE)
    private boolean secure;

    private Mode mode = Mode.SYNC;

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
        operationId = UUID.randomUUID().toString();
        roleSet = new HashSet<>();
        scopeSet = new HashSet<>();
        groupSet = new HashSet<>();
        groupSet.add(GROUP_EVERYONE);

        securityGroupSet = new HashSet<>();
        attributes  = new HashMap<>();
    }

    void regenerateID() {
        operationId = UUID.randomUUID().toString();
    }

    @Override
    public Mode getMode() {
        return mode == null ? Mode.SYNC : mode;
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

