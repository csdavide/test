package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Property   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String prefixedName = null;
  private Boolean multivalue = null;
  private String dataType = null;
  private List<String> values = new ArrayList<String>();
  private String relativeAspectPrefixedName = null;

  /**
   * Prefixed name dell&#39;eventuale aspetto che contiene la proprietà.
   **/
  

  @JsonProperty("prefixedName") 
 
  public String getPrefixedName() {
    return prefixedName;
  }
  public void setPrefixedName(String prefixedName) {
    this.prefixedName = prefixedName;
  }

  /**
   * Indica se la proprietà è multivalore oppure no.
   **/
  

  @JsonProperty("multivalue") 
 
  public Boolean isMultivalue() {
    return multivalue;
  }
  public void setMultivalue(Boolean multivalue) {
    this.multivalue = multivalue;
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
   * Valori della proprietà.
   **/
  

  @JsonProperty("values") 
 
  public List<String> getValues() {
    return values;
  }
  public void setValues(List<String> values) {
    this.values = values;
  }

  /**
   * Prefixed name dell&#39;eventuale aspetto che contiene la proprietà.
   **/
  

  @JsonProperty("relativeAspectPrefixedName") 
 
  public String getRelativeAspectPrefixedName() {
    return relativeAspectPrefixedName;
  }
  public void setRelativeAspectPrefixedName(String relativeAspectPrefixedName) {
    this.relativeAspectPrefixedName = relativeAspectPrefixedName;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Property property = (Property) o;
    return Objects.equals(prefixedName, property.prefixedName) &&
        Objects.equals(multivalue, property.multivalue) &&
        Objects.equals(dataType, property.dataType) &&
        Objects.equals(values, property.values) &&
        Objects.equals(relativeAspectPrefixedName, property.relativeAspectPrefixedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName, multivalue, dataType, values, relativeAspectPrefixedName);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Property {\n");
    
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    multivalue: ").append(toIndentedString(multivalue)).append("\n");
    sb.append("    dataType: ").append(toIndentedString(dataType)).append("\n");
    sb.append("    values: ").append(toIndentedString(values)).append("\n");
    sb.append("    relativeAspectPrefixedName: ").append(toIndentedString(relativeAspectPrefixedName)).append("\n");
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

