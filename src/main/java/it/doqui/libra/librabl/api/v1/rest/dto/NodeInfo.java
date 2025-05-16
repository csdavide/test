package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeInfo   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String uid = null;
  private String contentPropertyName = null;
  private Boolean enveloped = null;

  /**
   * UUID del nodo.
   **/
  

  @JsonProperty("uid") 
 
  public String getUid() {
    return uid;
  }
  public void setUid(String uid) {
    this.uid = uid;
  }

  /**
   * Il nome della proprietà di tipo cm:content che contiene il contenuto binario.
   **/
  

  @JsonProperty("contentPropertyName") 
 
  public String getContentPropertyName() {
    return contentPropertyName;
  }
  public void setContentPropertyName(String contentPropertyName) {
    this.contentPropertyName = contentPropertyName;
  }

  /**
   * Imposta se il nodo è imbustato.
   **/
  

  @JsonProperty("enveloped") 
 
  public Boolean isEnveloped() {
    return enveloped;
  }
  public void setEnveloped(Boolean enveloped) {
    this.enveloped = enveloped;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodeInfo nodeInfo = (NodeInfo) o;
    return Objects.equals(uid, nodeInfo.uid) &&
        Objects.equals(contentPropertyName, nodeInfo.contentPropertyName) &&
        Objects.equals(enveloped, nodeInfo.enveloped);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, contentPropertyName, enveloped);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class NodeInfo {\n");
    
    sb.append("    uid: ").append(toIndentedString(uid)).append("\n");
    sb.append("    contentPropertyName: ").append(toIndentedString(contentPropertyName)).append("\n");
    sb.append("    enveloped: ").append(toIndentedString(enveloped)).append("\n");
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

