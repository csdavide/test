package it.doqui.libra.librabl.cache;

import java.util.Optional;

public interface Cache<K, V> {
    boolean put(K key, V value);
    V putIfAbsent(K key, V value);
    Optional<V> get(K key);
    int size();
    boolean isEmpty();
    void clear();
}
