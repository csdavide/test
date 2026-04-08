package it.doqui.libra.librabl.infrastructure.platform.security;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Unremovable
public class SessionContextController {

    @Inject
    SessionContextHolder sessionContextHolder;

    public UserContext getUserContext() {
        return sessionContextHolder.getUserContext();
    }

    public SessionContext getSessionContext() {
        return sessionContextHolder;
    }
}
