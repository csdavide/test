package it.doqui.libra.librabl.foundation;

import java.time.Duration;

public class DurationFilter {

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Duration duration) {
            return duration.isZero();
        }

        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
