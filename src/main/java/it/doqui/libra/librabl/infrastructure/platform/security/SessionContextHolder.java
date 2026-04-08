package it.doqui.libra.librabl.infrastructure.platform.security;

import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.libra.librabl.infrastructure.platform.events.JobAbortEvent;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.Strings;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RequestScoped
@Getter
@Setter
@ToString
public class SessionContextHolder implements SessionContext {
    private UserContext userContext;
    private SessionMode mode = SessionMode.SYNC;
    private String operationId = UuidCreator.getTimeOrderedEpoch().toString();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private String channel;
    private String application;
    private String userIdentity;
    private int apiLevel;
    private final AtomicLong operationCounter = new AtomicLong(0);
    private String token;

    void onEvent(@Observes JobAbortEvent payload) {
        if (Strings.CS.equals(payload.getJobId(), operationId)) {
            cancelled.set(true);
        }
    }
}
