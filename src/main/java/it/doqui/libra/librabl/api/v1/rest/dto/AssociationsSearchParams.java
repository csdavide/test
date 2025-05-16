package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AssociationsSearchParams   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private Integer limit = null;
  private Integer pageSize = null;
  private Integer pageIndex = null;
  private List<String> filterType = new ArrayList<String>();

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
   * Dimesione della pagina da ricercare.
   **/
  

  @JsonProperty("pageSize") 
 
  public Integer getPageSize() {
    return pageSize;
  }
  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * Numero di pagina da ricercare.
   **/
  

  @JsonProperty("pageIndex") 
 
  public Integer getPageIndex() {
    return pageIndex;
  }
  public void setPageIndex(Integer pageIndex) {
    this.pageIndex = pageIndex;
  }

  /**
   * Tipi di associazioni da ricercare.
   **/
  

  @JsonProperty("filterType") 
 
  public List<String> getFilterType() {
    return filterType;
  }
  public void setFilterType(List<String> filterType) {
    this.filterType = filterType;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AssociationsSearchParams associationsSearchParams = (AssociationsSearchParams) o;
    return Objects.equals(limit, associationsSearchParams.limit) &&
        Objects.equals(pageSize, associationsSearchParams.pageSize) &&
        Objects.equals(pageIndex, associationsSearchParams.pageIndex) &&
        Objects.equals(filterType, associationsSearchParams.filterType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(limit, pageSize, pageIndex, filterType);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AssociationsSearchParams {\n");
    
    sb.append("    limit: ").append(toIndentedString(limit)).append("\n");
    sb.append("    pageSize: ").append(toIndentedString(pageSize)).append("\n");
    sb.append("    pageIndex: ").append(toIndentedString(pageIndex)).append("\n");
    sb.append("    filterType: ").append(toIndentedString(filterType)).append("\n");
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

