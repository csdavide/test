package it.doqui.libra.librabl.domain.ports.out;

import java.util.Optional;

public interface DictionaryRepository {
    Optional<String> getPayload(String ns, String key);
}
