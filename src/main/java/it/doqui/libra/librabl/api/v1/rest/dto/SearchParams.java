package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchParams   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String luceneQuery = null;
  private Integer pageSize = null;
  private Integer pageIndex = null;
  private Integer limit = null;
  private List<SearchParamsSortFields> sortFields = new ArrayList<SearchParamsSortFields>();

  /**
   * Query della ricerca.
   **/
  

  @JsonProperty("luceneQuery") 
 
  public String getLuceneQuery() {
    return luceneQuery;
  }
  public void setLuceneQuery(String luceneQuery) {
    this.luceneQuery = luceneQuery;
  }

  /**
   * Dimensione della pagina della ricerca.
   **/
  

  @JsonProperty("pageSize") 
 
  public Integer getPageSize() {
    return pageSize;
  }
  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * Indice della pagina della ricerca.
   **/
  

  @JsonProperty("pageIndex") 
 
  public Integer getPageIndex() {
    return pageIndex;
  }
  public void setPageIndex(Integer pageIndex) {
    this.pageIndex = pageIndex;
  }

  /**
   * Numero massimo di associazioni da ricercare.
   **/
  

  @JsonProperty("limit") 
 
  public Integer getLimit() {
    return limit;
  }
  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  /**
   * Campi su cui eseguire l&#39;ordinamento.
   **/
  

  @JsonProperty("sortFields") 
 
  public List<SearchParamsSortFields> getSortFields() {
    return sortFields;
  }
  public void setSortFields(List<SearchParamsSortFields> sortFields) {
    this.sortFields = sortFields;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SearchParams searchParams = (SearchParams) o;
    return Objects.equals(luceneQuery, searchParams.luceneQuery) &&
        Objects.equals(pageSize, searchParams.pageSize) &&
        Objects.equals(pageIndex, searchParams.pageIndex) &&
        Objects.equals(limit, searchParams.limit) &&
        Objects.equals(sortFields, searchParams.sortFields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(luceneQuery, pageSize, pageIndex, limit, sortFields);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SearchParams {\n");
    
    sb.append("    luceneQuery: ").append(toIndentedString(luceneQuery)).append("\n");
    sb.append("    pageSize: ").append(toIndentedString(pageSize)).append("\n");
    sb.append("    pageIndex: ").append(toIndentedString(pageIndex)).append("\n");
    sb.append("    limit: ").append(toIndentedString(limit)).append("\n");
    sb.append("    sortFields: ").append(toIndentedString(sortFields)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

