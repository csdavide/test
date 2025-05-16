package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AssociationMetadata   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private String title = null;
  private String description = null;
  private ModelDescriptor modelDescriptor = null;

  /**
   * Prefixed name dell&#39;elemento.
   **/
  

  @JsonProperty("prefixedName") 
 
  public String getPrefixedName() {
    return prefixedName;
  }
  public void setPrefixedName(String prefixedName) {
    this.prefixedName = prefixedName;
  }

  /**
   * Titolo dell&#39;elemento.
   **/
  

  @JsonProperty("title") 
 
  public String getTitle() {
    return title;
  }
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   **/
  

  @JsonProperty("description") 
 
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   **/
  

  @JsonProperty("modelDescriptor") 
 
  public ModelDescriptor getModelDescriptor() {
    return modelDescriptor;
  }
  public void setModelDescriptor(ModelDescriptor modelDescriptor) {
    this.modelDescriptor = modelDescriptor;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AssociationMetadata associationMetadata = (AssociationMetadata) o;
    return Objects.equals(prefixedName, associationMetadata.prefixedName) &&
        Objects.equals(title, associationMetadata.title) &&
        Objects.equals(description, associationMetadata.description) &&
        Objects.equals(modelDescriptor, associationMetadata.modelDescriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, title, description, modelDescriptor);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AssociationMetadata {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    modelDescriptor: ").append(toIndentedString(modelDescriptor)).append("\n");
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

