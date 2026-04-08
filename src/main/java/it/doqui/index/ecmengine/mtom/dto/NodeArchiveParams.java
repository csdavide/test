package it.doqui.index.ecmengine.mtom.dto;

/**
 * DTO che rappresenta i parametri per la restituzione dei contenuti cancellati.
 * 
 * @author DoQui
 */
public class NodeArchiveParams extends MtomEngineDto {
    private static final long serialVersionUID = 3100135075523489548L;

    private String typePrefixedName;
    private int pageSize;
    private int pageIndex;
    private int limit;
    private boolean typeAsAspect;

    /**
     * Restituisce il prefixed name del tipo dei nodi o di un aspetto dei nodi da
     * usare come filtro della ricerca.
     */
    public String getTypePrefixedName() {
	return typePrefixedName;
    }

    /**
     * Imposta il prefixed name del tipo dei nodi o di un aspetto dei nodi da usare
     * come filtro della ricerca.
     */
    public void setTypePrefixedName(String typePrefixedName) {
	this.typePrefixedName = typePrefixedName;
    }

    /**
     * Restituisce la dimensione della pagina dei risultati della ricerca.
     */
    public int getPageSize() {
	return pageSize;
    }

    /**
     * Imposta la dimensione della pagina dei risultati della ricerca.
     */
    public void setPageSize(int pageSize) {
	this.pageSize = pageSize;
    }

    /**
     * Restituisce l'indice della pagina dei risultati della ricerca.
     */
    public int getPageIndex() {
	return pageIndex;
    }

    /**
     * Imposta l'indice della pagina dei risultati della ricerca.
     */
    public void setPageIndex(int pageIndex) {
	this.pageIndex = pageIndex;
    }

    /**
     * Restituisce il numero massimo di risultati della ricerca da restituire.
     */
    public int getLimit() {
	return limit;
    }

    /**
     * Imposta il numero massimo di risultati della ricerca da restituire.
     */
    public void setLimit(int limit) {
	this.limit = limit;
    }

    /**
     * Restituisce {@code true} se il prefixed name da usare come filtro definisce
     * il tipo di un nodo, {@code false} se definisce un aspetto.
     */
    public boolean isTypeAsAspect() {
	return typeAsAspect;
    }

    /**
     * Impostare {@code true} se il prefixed name da usare come filtro definisce il
     * tipo di un nodo, {@code false} se definisce un aspetto.
     */
    public void setTypeAsAspect(boolean typeAsAspect) {
	this.typeAsAspect = typeAsAspect;
    }
}