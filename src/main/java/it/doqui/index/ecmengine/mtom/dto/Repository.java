package it.doqui.index.ecmengine.mtom.dto;

import java.util.Arrays;

/**
 * DTO che rappresenta un repository fisico.
 *
 * @author DoQui
 */

public class Repository extends MtomEngineDto {
    private static final long serialVersionUID = -4852447349533448056L;

    private String id;

    // MB: per gestione MultiContentStore
    private ContentStoreDefinition[] contentStore;

    /**
     * Crea un nuovo {@code Repository}.
     */
    public Repository() {
        super();
    }

    /**
     * Crea un nuovo {@code Repository}.
     *
     * @param id l'id del repository
     */
    public Repository(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'id del repository.
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'id del repository.
     *
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'array di {@link ContentStoreDefinition} attivi su questo
     * repository.
     */
    public ContentStoreDefinition[] getContentStores() {
        return contentStore;
    }

    /**
     * Imposta l'array di {@link ContentStoreDefinition} attivi su questo
     * repository.
     */
    public void setContentStores(ContentStoreDefinition[] contentStore) {
        this.contentStore = contentStore;
    }

    public String toString() {
        return "Repository [id=" + id + ", contentStore=" + Arrays.toString(contentStore) + "]";
    }
}