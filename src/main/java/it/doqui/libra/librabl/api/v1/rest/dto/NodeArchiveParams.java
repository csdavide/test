package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeArchiveParams   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String typePrefixedName = null;
  private Integer pageSize = null;
  private Integer pageIndex = null;
  private Integer limit = null;
  private Boolean typeAsAspect = null;

  /**
   * Prefixed name del tipo dei nodi o di un aspetto dei nodi da usare come filtro della ricerca.
   **/
  

  @JsonProperty("typePrefixedName") 
 
  public String getTypePrefixedName() {
    return typePrefixedName;
  }
  public void setTypePrefixedName(String typePrefixedName) {
    this.typePrefixedName = typePrefixedName;
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
   * Impostare true se il prefixed name da usare come filtro definisce il tipo di un nodo, false se definisce un aspetto.
   **/
  

  @JsonProperty("typeAsAspect") 
 
  public Boolean isTypeAsAspect() {
    return typeAsAspect;
  }
  public void setTypeAsAspect(Boolean typeAsAspect) {
    this.typeAsAspect = typeAsAspect;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodeArchiveParams nodeArchiveParams = (NodeArchiveParams) o;
    return Objects.equals(typePrefixedName, nodeArchiveParams.typePrefixedName) &&
        Objects.equals(pageSize, nodeArchiveParams.pageSize) &&
        Objects.equals(pageIndex, nodeArchiveParams.pageIndex) &&
        Objects.equals(limit, nodeArchiveParams.limit) &&
        Objects.equals(typeAsAspect, nodeArchiveParams.typeAsAspect);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typePrefixedName, pageSize, pageIndex, limit, typeAsAspect);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class NodeArchiveParams {\n");
    
    sb.append("    typePrefixedName: ").append(toIndentedString(typePrefixedName)).append("\n");
    sb.append("    pageSize: ").append(toIndentedString(pageSize)).append("\n");
    sb.append("    pageIndex: ").append(toIndentedString(pageIndex)).append("\n");
    sb.append("    limit: ").append(toIndentedString(limit)).append("\n");
    sb.append("    typeAsAspect: ").append(toIndentedString(typeAsAspect)).append("\n");
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

