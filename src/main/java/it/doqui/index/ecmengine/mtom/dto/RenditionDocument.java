package it.doqui.index.ecmengine.mtom.dto;

public class RenditionDocument extends Content {

    private static final long serialVersionUID = 5302298231378119482L;

    private String nodeId;
    private String description;
    private boolean tempNode;

    /**
     * <p>
     * Restituisce l'UID del nodo della rendition.
     * </p>
     * <p>
     * Se l'UID non &egrave; presente, resituisce {@code null}.
     * </p>
     */
    public String getNodeId() {
	return nodeId;
    }

    /**
     * Imposta l'UID del nodo della rendition.
     */
    public void setNodeId(String nodeId) {
	this.nodeId = nodeId;
    }

    /**
     * Restituisce la descrizione della rendition.
     */
    public String getDescription() {
	return description;
    }

    /**
     * Imposta la descrizione della rendition
     * 
     * @param description
     */
    public void setDescription(String description) {
	this.description = description;
    }

    /**
     * Indica se la rendition &egrace; presente in un nodo temporaneo.
     */
    public boolean isTempNode() {
	return tempNode;
    }

    /**
     * Imposta se la rendition &egrace; presente in un nodo temporaneo.
     */
    public void setTempNode(boolean tempNode) {
	this.tempNode = tempNode;
    }
}
