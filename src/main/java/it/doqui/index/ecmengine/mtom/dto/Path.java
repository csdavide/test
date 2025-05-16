package it.doqui.index.ecmengine.mtom.dto;

public class Path extends MtomEngineDto {

    private static final long serialVersionUID = 2061892168919577398L;

    private String path;
    private boolean primary;

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

    /**
     * <p>
     * Restituisce il valore del flag che caratterizza il path come
     * &quot;primario&quot;.
     * </p>
     * <p>
     * Un path primario &egrave; un path costruito ripercorrendo le associazioni
     * primarie di un nodo. <i>Esiste un solo path primario per nodo.</i>
     * </p>
     */
    public boolean isPrimary() {
	return primary;
    }

    /**
     * <p>
     * Imposta il valore del flag che caratterizza il path come
     * &quot;primario&quot;.
     * </p>
     * <p>
     * Un path primario &egrave; un path costruito ripercorrendo le associazioni
     * primarie di un nodo. <i>Esiste un solo path primario per nodo.</i>
     * </p>
     * 
     * @param primary {@code true} se il path &egrave; primario, {@code false}
     *                altrimenti.
     */
    public void setPrimary(boolean primary) {
	this.primary = primary;
    }

}
