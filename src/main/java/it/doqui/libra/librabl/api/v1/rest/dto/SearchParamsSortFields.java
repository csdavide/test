package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchParamsSortFields   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String fieldName = null;
  private Boolean ascending = null;

  /**
   * Nome del metadato utilizzato per l&#39;ordinamento.
   **/
  

  @JsonProperty("fieldName") 
 
  public String getFieldName() {
    return fieldName;
  }
  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  /**
   * Imposta se l&#39;ordinamento deve essere crescente o decrescente.
   **/
  

  @JsonProperty("ascending") 
 
  public Boolean isAscending() {
    return ascending;
  }
  public void setAscending(Boolean ascending) {
    this.ascending = ascending;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SearchParamsSortFields searchParamsSortFields = (SearchParamsSortFields) o;
    return Objects.equals(fieldName, searchParamsSortFields.fieldName) &&
        Objects.equals(ascending, searchParamsSortFields.ascending);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fieldName, ascending);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SearchParamsSortFields {\n");
    
    sb.append("    fieldName: ").append(toIndentedString(fieldName)).append("\n");
    sb.append("    ascending: ").append(toIndentedString(ascending)).append("\n");
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

