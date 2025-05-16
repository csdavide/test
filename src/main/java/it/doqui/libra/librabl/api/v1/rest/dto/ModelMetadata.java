package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelMetadata   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private String description = null;
  private List<ModelMetadataTypes> types = new ArrayList<ModelMetadataTypes>();
  private List<AspectMetadata> aspects = new ArrayList<AspectMetadata>();

  /**
   * Prefixed name del modello.
   **/
  

  @JsonProperty("prefixedName") 
 
  public String getPrefixedName() {
    return prefixedName;
  }
  public void setPrefixedName(String prefixedName) {
    this.prefixedName = prefixedName;
  }

  /**
   * Descrizione del modello.
   **/
  

  @JsonProperty("description") 
 
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Definizione dei tipi del modello.
   **/
  

  @JsonProperty("types") 
 
  public List<ModelMetadataTypes> getTypes() {
    return types;
  }
  public void setTypes(List<ModelMetadataTypes> types) {
    this.types = types;
  }

  /**
   * Definizione degli aspetti del modello.
   **/
  

  @JsonProperty("aspects") 
 
  public List<AspectMetadata> getAspects() {
    return aspects;
  }
  public void setAspects(List<AspectMetadata> aspects) {
    this.aspects = aspects;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ModelMetadata modelMetadata = (ModelMetadata) o;
    return Objects.equals(prefixedName, modelMetadata.prefixedName) &&
        Objects.equals(description, modelMetadata.description) &&
        Objects.equals(types, modelMetadata.types) &&
        Objects.equals(aspects, modelMetadata.aspects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, description, types, aspects);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ModelMetadata {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    types: ").append(toIndentedString(types)).append("\n");
    sb.append("    aspects: ").append(toIndentedString(aspects)).append("\n");
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

