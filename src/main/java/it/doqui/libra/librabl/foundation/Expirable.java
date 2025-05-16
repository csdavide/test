package it.doqui.libra.librabl.foundation;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

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
        return expires != null && expires.isBefore(ZonedDateTime.now());
    }
}
