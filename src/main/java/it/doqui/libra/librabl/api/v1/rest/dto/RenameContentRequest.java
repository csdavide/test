package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RenameContentRequest   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String nameValue = null;
  private String propertyPrefixedName = null;
  private Boolean onlyPrimaryAssociation = null;

  /**
   * Il nuovo nome da assegnare al nodo.
   **/
  

  @JsonProperty("nameValue") 
 
  public String getNameValue() {
    return nameValue;
  }
  public void setNameValue(String nameValue) {
    this.nameValue = nameValue;
  }

  /**
   * Il nome della proprietà di tipo cm:name che rappresenta il nome del nodo;    &gt;     impostare null per specificare il default cm:name. 
   **/
  

  @JsonProperty("propertyPrefixedName") 
 
  public String getPropertyPrefixedName() {
    return propertyPrefixedName;
  }
  public void setPropertyPrefixedName(String propertyPrefixedName) {
    this.propertyPrefixedName = propertyPrefixedName;
  }

  /**
   * Specifica se l&#39;operazione di rename deve essere applicata solo alla associazione padre primaria
   **/
  

  @JsonProperty("onlyPrimaryAssociation") 
 
  public Boolean isOnlyPrimaryAssociation() {
    return onlyPrimaryAssociation;
  }
  public void setOnlyPrimaryAssociation(Boolean onlyPrimaryAssociation) {
    this.onlyPrimaryAssociation = onlyPrimaryAssociation;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RenameContentRequest renameContentRequest = (RenameContentRequest) o;
    return Objects.equals(nameValue, renameContentRequest.nameValue) &&
        Objects.equals(propertyPrefixedName, renameContentRequest.propertyPrefixedName) &&
        Objects.equals(onlyPrimaryAssociation, renameContentRequest.onlyPrimaryAssociation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nameValue, propertyPrefixedName, onlyPrimaryAssociation);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RenameContentRequest {\n");
    
    sb.append("    nameValue: ").append(toIndentedString(nameValue)).append("\n");
    sb.append("    propertyPrefixedName: ").append(toIndentedString(propertyPrefixedName)).append("\n");
    sb.append("    onlyPrimaryAssociation: ").append(toIndentedString(onlyPrimaryAssociation)).append("\n");
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

