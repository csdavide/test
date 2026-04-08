package it.doqui.index.ecmengine.mtom.dto;

public class RenditionTransformer extends Content {

    private static final long serialVersionUID = 4126514504615206757L;

    private String nodeId;
    private String description;
    private String genMymeType;
    private boolean tempNode;
    private String renditionUid;

    /**
     * <p>
     * Restituisce l'UID del nodo del rendition transformer.
     * </p>
     * <p>
     * Se l'UID non &egrave; presente, resituisce {@code null}.
     * </p>
     */
    public String getNodeId() {
	return nodeId;
    }

    /**
     * Imposta l'UID del nodo del rendition transformer.
     */
    public void setNodeId(String nodeId) {
	this.nodeId = nodeId;
    }

    /**
     * Resituisce la descrizione del rendition transformer.
     */
    public String getDescription() {
	return description;
    }

    /**
     * Imposta la descrizione del rendition transformer.
     */
    public void setDescription(String description) {
	this.description = description;
    }

    /**
     * Restiusice il MIME type di resa.
     */
    public String getGenMymeType() {
	return genMymeType;
    }

    /**
     * Imposta il MIME type di resa.
     * 
     * @param genMymeType
     */
    public void setGenMymeType(String genMymeType) {
	this.genMymeType = genMymeType;
    }

    /**
     * Indica se il rendition transformer &egrace; presente in un nodo temporaneo.
     */
    public boolean isTempNode() {
	return tempNode;
    }

    /**
     * Imposta se il rendition transformer &egrace; presente in un nodo temporaneo.
     */
    public void setTempNode(boolean tempNode) {
	this.tempNode = tempNode;
    }

    /**
     * Resituisce l'UID della rendition.
     */
    public String getRenditionUid() {
	return renditionUid;
    }

    /**
     * Imposta l'UID della rendition.
     */
    public void setRenditionUid(String renditionUid) {
	this.renditionUid = renditionUid;
    }
}
