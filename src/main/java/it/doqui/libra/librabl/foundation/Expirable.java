package it.doqui.libra.librabl.foundation;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.Optional;

@Getter
@Setter
public class Expirable <T> {
    private T object;
    private ZonedDateTime expires;

    public Expirable(T object, ZonedDateTime expires) {
        this.object = object;
        this.expires = expires;
    }

    public Expirable() {
        this(null, null);
    }

    public boolean isExpired() {
        return wasExpiredAt(ZonedDateTime.now());
    }

    public boolean wasExpiredAt(ZonedDateTime t) {
        return expires != null && expires.isBefore(Optional.ofNullable(t).orElse(ZonedDateTime.now()));
    }
}
