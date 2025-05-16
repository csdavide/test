package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AspectMetadata   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private String title = null;
  private String description = null;
  private ModelDescriptor modelDescriptor = null;
  private List<PropertyMetadata> properties = new ArrayList<PropertyMetadata>();
  private List<AssociationMetadata> associations = new ArrayList<AssociationMetadata>();
  private List<AssociationMetadata> childAssociations = new ArrayList<AssociationMetadata>();
  private String parentPrefixedName = null;

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
   * Definizioni delle proprietà dell&#39;aspetto.
   **/
  

  @JsonProperty("properties") 
 
  public List<PropertyMetadata> getProperties() {
    return properties;
  }
  public void setProperties(List<PropertyMetadata> properties) {
    this.properties = properties;
  }

  /**
   * definizioni delle associazioni sorgente-destinazione dell&#39;aspetto.
   **/
  

  @JsonProperty("associations") 
 
  public List<AssociationMetadata> getAssociations() {
    return associations;
  }
  public void setAssociations(List<AssociationMetadata> associations) {
    this.associations = associations;
  }

  /**
   * Definizioni delle associazioni padre-figlio dell&#39;aspetto.
   **/
  

  @JsonProperty("childAssociations") 
 
  public List<AssociationMetadata> getChildAssociations() {
    return childAssociations;
  }
  public void setChildAssociations(List<AssociationMetadata> childAssociations) {
    this.childAssociations = childAssociations;
  }

  /**
   * Prefixed name dell&#39;aspetto padre, se presente, altrimenti null.
   **/
  

  @JsonProperty("parentPrefixedName") 
 
  public String getParentPrefixedName() {
    return parentPrefixedName;
  }
  public void setParentPrefixedName(String parentPrefixedName) {
    this.parentPrefixedName = parentPrefixedName;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AspectMetadata aspectMetadata = (AspectMetadata) o;
    return Objects.equals(prefixedName, aspectMetadata.prefixedName) &&
        Objects.equals(title, aspectMetadata.title) &&
        Objects.equals(description, aspectMetadata.description) &&
        Objects.equals(modelDescriptor, aspectMetadata.modelDescriptor) &&
        Objects.equals(properties, aspectMetadata.properties) &&
        Objects.equals(associations, aspectMetadata.associations) &&
        Objects.equals(childAssociations, aspectMetadata.childAssociations) &&
        Objects.equals(parentPrefixedName, aspectMetadata.parentPrefixedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, title, description, modelDescriptor, properties, associations, childAssociations, parentPrefixedName);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AspectMetadata {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    modelDescriptor: ").append(toIndentedString(modelDescriptor)).append("\n");
    sb.append("    properties: ").append(toIndentedString(properties)).append("\n");
    sb.append("    associations: ").append(toIndentedString(associations)).append("\n");
    sb.append("    childAssociations: ").append(toIndentedString(childAssociations)).append("\n");
    sb.append("    parentPrefixedName: ").append(toIndentedString(parentPrefixedName)).append("\n");
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

