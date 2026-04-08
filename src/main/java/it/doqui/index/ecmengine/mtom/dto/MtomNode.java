package it.doqui.index.ecmengine.mtom.dto;

/**
 * Classe DTO che rappresenta un nodo di ECMEngine.
 * 
 * @author DoQui
 *
 */
public class MtomNode {
    private String uid;

    private String prefixedName;

    public MtomNode() {
	super();
    }

    public MtomNode(String uid, String prefixedName) {
	super();
	this.uid = uid;
	this.prefixedName = prefixedName;
    }

    /**
     * Restituisce l'uid del nodo.
     * 
     * @return L'uid del nodo.
     */
    public String getUid() {
	return uid;
    }

    /**
     * Imposta l'uid del nodo.
     * 
     * @param uid L'uid del nodo.
     */
    public void setUid(String uid) {
	this.uid = uid;
    }

    /**
     * Restituisce il prefixedname del nodo.
     * 
     * @return Il prefixedname del nodo.
     */
    public String getPrefixedName() {
	return prefixedName;
    }

    /**
     * Imposta il prefixedname del nodo.
     * 
     * @param prefixedName Il prefixedname del nodo.
     */
    public void setPrefixedName(String prefixedName) {
	this.prefixedName = prefixedName;
    }
}
