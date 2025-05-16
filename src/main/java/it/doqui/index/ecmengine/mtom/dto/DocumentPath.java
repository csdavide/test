package it.doqui.index.ecmengine.mtom.dto;

public class DocumentPath extends MtomEngineDto {

    private static final long serialVersionUID = 2061892168919577398L;

    private String path;

    private String mimetype;
    private long contentLength;

    private String fileName;

    /**
     * Restituisce il percorso.
     */
    public String getPath() {
	return path;
    }

    /**
     * Imposta il percorso.
     */
    public void setPath(String path) {
	this.path = path;
    }

    public String getMimetype() {
	return mimetype;
    }

    public void setMimetype(String mimetype) {
	this.mimetype = mimetype;
    }

    public long getContentLength() {
	return contentLength;
    }

    public void setContentLength(long contentLength) {
	this.contentLength = contentLength;
    }

    public String getFileName() {
	return fileName;
    }

    public void setFileName(String fileName) {
	this.fileName = fileName;
    }

}
