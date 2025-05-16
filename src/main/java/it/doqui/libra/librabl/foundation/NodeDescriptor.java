package it.doqui.libra.librabl.foundation;

import java.util.Map;
import java.util.Set;

public interface NodeDescriptor {
    String getTypeName();
    Set<String> getAspects();
    Map<String, Object> getProperties();
}
