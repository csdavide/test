package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Association   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private String typePrefixedName = null;
  private Boolean childAssociation = null;

  /**
   * Prefixed name del nome dell&#39;associazione.
   **/
  

  @JsonProperty("prefixedName") 
 
  public String getPrefixedName() {
    return prefixedName;
  }
  public void setPrefixedName(String prefixedName) {
    this.prefixedName = prefixedName;
  }

  /**
   * Prefixed name del tipo dell&#39;associazione.
   **/
  

  @JsonProperty("typePrefixedName") 
 
  public String getTypePrefixedName() {
    return typePrefixedName;
  }
  public void setTypePrefixedName(String typePrefixedName) {
    this.typePrefixedName = typePrefixedName;
  }

  /**
   * Indica se l&#39;associazione è di tipo padre/figlio o di tipo sorgente/destinazione.
   **/
  

  @JsonProperty("childAssociation") 
 
  public Boolean isChildAssociation() {
    return childAssociation;
  }
  public void setChildAssociation(Boolean childAssociation) {
    this.childAssociation = childAssociation;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Association association = (Association) o;
    return Objects.equals(prefixedName, association.prefixedName) &&
        Objects.equals(typePrefixedName, association.typePrefixedName) &&
        Objects.equals(childAssociation, association.childAssociation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, typePrefixedName, childAssociation);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Association {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    typePrefixedName: ").append(toIndentedString(typePrefixedName)).append("\n");
    sb.append("    childAssociation: ").append(toIndentedString(childAssociation)).append("\n");
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

