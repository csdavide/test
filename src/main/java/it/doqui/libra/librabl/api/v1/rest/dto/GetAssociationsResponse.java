package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GetAssociationsResponse   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private Long totalResults = null;
  private List<ResultAssociation> associations = new ArrayList<ResultAssociation>();

  /**
   * Numero totali di risultati della ricerca.
   **/
  

  @JsonProperty("totalResults") 
 
  public Long getTotalResults() {
    return totalResults;
  }
  public void setTotalResults(Long totalResults) {
    this.totalResults = totalResults;
  }

  /**
   * Elenco di associazioni risultato della ricerca.
   **/
  

  @JsonProperty("associations") 
 
  public List<ResultAssociation> getAssociations() {
    return associations;
  }
  public void setAssociations(List<ResultAssociation> associations) {
    this.associations = associations;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GetAssociationsResponse getAssociationsResponse = (GetAssociationsResponse) o;
    return Objects.equals(totalResults, getAssociationsResponse.totalResults) &&
        Objects.equals(associations, getAssociationsResponse.associations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(totalResults, associations);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GetAssociationsResponse {\n");
    
    sb.append("    totalResults: ").append(toIndentedString(totalResults)).append("\n");
    sb.append("    associations: ").append(toIndentedString(associations)).append("\n");
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

