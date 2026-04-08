package it.doqui.index.ecmengine.mtom.dto;

public class SearchParams extends MtomEngineDto {
    private static final long serialVersionUID = -6824769815720267423L;

    private int limit;
    private String luceneQuery;
    private String xPathQuery;
    private int pageSize;
    private int pageIndex;
    private SortField[] sortFields;

    public SearchParams() {
	super();
    }

    public SearchParams(int limit, String luceneQuery, int pageSize, int pageIndex, SortField[] sortFields) {
	super();
	this.limit = limit;
	this.luceneQuery = luceneQuery;
	this.pageSize = pageSize;
	this.pageIndex = pageIndex;
	this.sortFields = sortFields;
    }

    public int getLimit() {
	return limit;
    }

    public void setLimit(int limit) {
	this.limit = limit;
    }

    public String getLuceneQuery() {
	return luceneQuery;
    }

    public void setLuceneQuery(String luceneQuery) {
	this.luceneQuery = luceneQuery;
    }

    public int getPageSize() {
	return pageSize;
    }

    public void setPageSize(int pageSize) {
	this.pageSize = pageSize;
    }

    public int getPageIndex() {
	return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
	this.pageIndex = pageIndex;
    }

    public SortField[] getSortFields() {
	return sortFields;
    }

    public void setSortFields(SortField[] sortFields) {
	this.sortFields = sortFields;
    }

    public String getXPathQuery() {
	return xPathQuery;
    }

    public void setXPathQuery(String xPathQuery) {
	this.xPathQuery = xPathQuery;
    }
}