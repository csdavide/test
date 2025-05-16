package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;

import java.net.URI;
import java.net.URISyntaxException;

public interface NodeReferenceable {
    String getTenant();
    String getUuid();

    @JsonIgnore
    default URI getURI() {
        try {
            return new URI(String.format("%s://@%s@SpacesStore/%s", "node", getTenant(), getUuid()));
        } catch (URISyntaxException e) {
            throw new SystemException(e);
        }
    }

    @JsonIgnore
    default boolean isNodeUndefined() {
        return getUuid() == null || getUuid().isBlank();
    }
}
