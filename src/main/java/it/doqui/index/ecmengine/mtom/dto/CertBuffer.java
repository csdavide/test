package it.doqui.index.ecmengine.mtom.dto;

public class CertBuffer extends MtomEngineDto {
    private static final long serialVersionUID = 4779283132003202859L;

    //@JsonIgnore
    private byte[] data;
    private boolean store;

    /**
     * Restituisce il contenuto binario del certificato.
     */
    public byte[] getData() {
	return data;
    }

    /**
     * Imposta il contenuto binario del certificato.
     */
    public void setData(byte[] data) {
	this.data = data;
    }

    /**
     * Indica se il contenuto dovr&agrave essere salvato nel repository dopo
     * l'operazione richiesta.
     */
    public boolean isStore() {
	return store;
    }

    /**
     * Imposta se il contenuto dovr&agrave essere salvato nel repository dopo
     * l'operazione richiesta.
     */
    public void setStore(boolean store) {
	this.store = store;
    }
}