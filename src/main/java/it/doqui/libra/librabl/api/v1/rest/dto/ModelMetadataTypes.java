package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelMetadataTypes   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private String title = null;
  private String description = null;
  private ModelDescriptor modelDescriptor = null;
  private String parentPrefixedName = null;
  private List<AspectMetadata> aspects = new ArrayList<AspectMetadata>();
  private List<PropertyMetadata> properties = new ArrayList<PropertyMetadata>();
  private List<AssociationMetadata> associations = new ArrayList<AssociationMetadata>();
  private List<AssociationMetadata> childAssociations = new ArrayList<AssociationMetadata>();

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
   * Descrizione dell&#39;elemento.
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

  /**
   * Nome completo di prefisso del tipo padre di questo tipo.
   **/
  

  @JsonProperty("parentPrefixedName") 
 
  public String getParentPrefixedName() {
    return parentPrefixedName;
  }
  public void setParentPrefixedName(String parentPrefixedName) {
    this.parentPrefixedName = parentPrefixedName;
  }

  /**
   * Metadati degli aspect definiti in questo tipo.
   **/
  

  @JsonProperty("aspects") 
 
  public List<AspectMetadata> getAspects() {
    return aspects;
  }
  public void setAspects(List<AspectMetadata> aspects) {
    this.aspects = aspects;
  }

  /**
   * Metadati delle property definite in questo tipo.
   **/
  

  @JsonProperty("properties") 
 
  public List<PropertyMetadata> getProperties() {
    return properties;
  }
  public void setProperties(List<PropertyMetadata> properties) {
    this.properties = properties;
  }

  /**
   * Metadati delle associazioni semplici definite in questo tipo.
   **/
  

  @JsonProperty("associations") 
 
  public List<AssociationMetadata> getAssociations() {
    return associations;
  }
  public void setAssociations(List<AssociationMetadata> associations) {
    this.associations = associations;
  }

  /**
   * Metadati delle associazioni padre-figlio definite in questo tipo.
   **/
  

  @JsonProperty("childAssociations") 
 
  public List<AssociationMetadata> getChildAssociations() {
    return childAssociations;
  }
  public void setChildAssociations(List<AssociationMetadata> childAssociations) {
    this.childAssociations = childAssociations;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ModelMetadataTypes modelMetadataTypes = (ModelMetadataTypes) o;
    return Objects.equals(prefixedName, modelMetadataTypes.prefixedName) &&
        Objects.equals(title, modelMetadataTypes.title) &&
        Objects.equals(description, modelMetadataTypes.description) &&
        Objects.equals(modelDescriptor, modelMetadataTypes.modelDescriptor) &&
        Objects.equals(parentPrefixedName, modelMetadataTypes.parentPrefixedName) &&
        Objects.equals(aspects, modelMetadataTypes.aspects) &&
        Objects.equals(properties, modelMetadataTypes.properties) &&
        Objects.equals(associations, modelMetadataTypes.associations) &&
        Objects.equals(childAssociations, modelMetadataTypes.childAssociations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, title, description, modelDescriptor, parentPrefixedName, aspects, properties, associations, childAssociations);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ModelMetadataTypes {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    modelDescriptor: ").append(toIndentedString(modelDescriptor)).append("\n");
    sb.append("    parentPrefixedName: ").append(toIndentedString(parentPrefixedName)).append("\n");
    sb.append("    aspects: ").append(toIndentedString(aspects)).append("\n");
    sb.append("    properties: ").append(toIndentedString(properties)).append("\n");
    sb.append("    associations: ").append(toIndentedString(associations)).append("\n");
    sb.append("    childAssociations: ").append(toIndentedString(childAssociations)).append("\n");
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

