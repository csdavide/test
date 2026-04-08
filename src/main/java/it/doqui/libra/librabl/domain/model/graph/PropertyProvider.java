package it.doqui.libra.librabl.domain.model.graph;

public interface PropertyProvider {
    Object getProperty(String name);
    boolean hasAspect(String name);
}
