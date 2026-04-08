package it.doqui.index.ecmengine.mtom.dto;

public class SearchResponse extends MtomEngineDto {
    private static final long serialVersionUID = 5431775876642526488L;
    
    private int totalResults;
    private int pageSize;
    private int pageIndex;
    private Content[] contentArray;

    public SearchResponse() {
	super();
	// TODO Auto-generated constructor stub
    }

    public SearchResponse(int totalResults, int pageSize, int pageIndex, Content[] contentArray) {
	super();
	this.totalResults = totalResults;
	this.pageSize = pageSize;
	this.pageIndex = pageIndex;
	this.contentArray = contentArray;
    }

    public int getTotalResults() {
	return totalResults;
    }

    public void setTotalResults(int totalResults) {
	this.totalResults = totalResults;
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

    public Content[] getContentArray() {
	return contentArray;
    }

    public void setContentArray(Content[] contentArray) {
	this.contentArray = contentArray;
    }

}
