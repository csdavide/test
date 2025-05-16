package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PropertyMetadata   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private String title = null;
  private String description = null;
  private ModelDescriptor modelDescriptor = null;
  private String dataType = null;
  private Boolean mandatory = null;
  private Boolean multiValued = null;
  private Boolean modifiable = null;

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
   * Tipo della proprietà.
   **/
  

  @JsonProperty("dataType") 
 
  public String getDataType() {
    return dataType;
  }
  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  /**
   * Indica se la proprietà è obbligatoria oppure no.
   **/
  

  @JsonProperty("mandatory") 
 
  public Boolean isMandatory() {
    return mandatory;
  }
  public void setMandatory(Boolean mandatory) {
    this.mandatory = mandatory;
  }

  /**
   * Indica se la proprietà è multivalore oppure no.
   **/
  

  @JsonProperty("multiValued") 
 
  public Boolean isMultiValued() {
    return multiValued;
  }
  public void setMultiValued(Boolean multiValued) {
    this.multiValued = multiValued;
  }

  /**
   * Indica se la proprietà è modificabile oppure no.
   **/
  

  @JsonProperty("modifiable") 
 
  public Boolean isModifiable() {
    return modifiable;
  }
  public void setModifiable(Boolean modifiable) {
    this.modifiable = modifiable;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PropertyMetadata propertyMetadata = (PropertyMetadata) o;
    return Objects.equals(prefixedName, propertyMetadata.prefixedName) &&
        Objects.equals(title, propertyMetadata.title) &&
        Objects.equals(description, propertyMetadata.description) &&
        Objects.equals(modelDescriptor, propertyMetadata.modelDescriptor) &&
        Objects.equals(dataType, propertyMetadata.dataType) &&
        Objects.equals(mandatory, propertyMetadata.mandatory) &&
        Objects.equals(multiValued, propertyMetadata.multiValued) &&
        Objects.equals(modifiable, propertyMetadata.modifiable);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, title, description, modelDescriptor, dataType, mandatory, multiValued, modifiable);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PropertyMetadata {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    modelDescriptor: ").append(toIndentedString(modelDescriptor)).append("\n");
    sb.append("    dataType: ").append(toIndentedString(dataType)).append("\n");
    sb.append("    mandatory: ").append(toIndentedString(mandatory)).append("\n");
    sb.append("    multiValued: ").append(toIndentedString(multiValued)).append("\n");
    sb.append("    modifiable: ").append(toIndentedString(modifiable)).append("\n");
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

