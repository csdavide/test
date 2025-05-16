package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Node {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled]

  private String uid = null;
  private String typePrefixedName = null;
  private String prefixedName = null;
  private String modelPrefixedName = null;
  private String parentAssocTypePrefixedName = null;
  private String contentPropertyPrefixedName = null;
  private String mimeType = null;
  private String encoding = null;
  private EncryptionInfo encryptionInfo = null;
  private List<Property> properties = new ArrayList<Property>();
  private List<Aspect> aspects = new ArrayList<Aspect>();

  /**
   * UID del nodo.
   **/


  @JsonProperty("uid")

  public String getUid() {
    return uid;
  }
  public void setUid(String uid) {
    this.uid = uid;
  }

  /**
   * Prefixed name del tipo del nodo.
   **/


  @JsonProperty("typePrefixedName")

  public String getTypePrefixedName() {
    return typePrefixedName;
  }
  public void setTypePrefixedName(String typePrefixedName) {
    this.typePrefixedName = typePrefixedName;
  }

  /**
   * Rappresenta il prefixed name.
   **/


  @JsonProperty("prefixedName")

  public String getPrefixedName() {
    return prefixedName;
  }
  public void setPrefixedName(String prefixedName) {
    this.prefixedName = prefixedName;
  }

  /**
   * Prefixed name del modello del nodo.
   **/


  @JsonProperty("modelPrefixedName")

  public String getModelPrefixedName() {
    return modelPrefixedName;
  }
  public void setModelPrefixedName(String modelPrefixedName) {
    this.modelPrefixedName = modelPrefixedName;
  }

  /**
   * Prefixed name dell&#39;associazione padre/figlio del nodo.
   **/


  @JsonProperty("parentAssocTypePrefixedName")

  public String getParentAssocTypePrefixedName() {
    return parentAssocTypePrefixedName;
  }
  public void setParentAssocTypePrefixedName(String parentAssocTypePrefixedName) {
    this.parentAssocTypePrefixedName = parentAssocTypePrefixedName;
  }

  /**
   * Prefixed name della proprietà che rappresenta il contenuto binario del nodo.
   **/


  @JsonProperty("contentPropertyPrefixedName")

  public String getContentPropertyPrefixedName() {
    return contentPropertyPrefixedName;
  }
  public void setContentPropertyPrefixedName(String contentPropertyPrefixedName) {
    this.contentPropertyPrefixedName = contentPropertyPrefixedName;
  }

  /**
   * MIME-Type del contenuto binario del nodo.
   **/


  @JsonProperty("mimeType")

  public String getMimeType() {
    return mimeType;
  }
  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  /**
   * Codifica del contenuto binario del nodo.
   **/


  @JsonProperty("encoding")

  public String getEncoding() {
    return encoding;
  }
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   **/


  @JsonProperty("encryptionInfo")

  public EncryptionInfo getEncryptionInfo() {
    return encryptionInfo;
  }
  public void setEncryptionInfo(EncryptionInfo encryptionInfo) {
    this.encryptionInfo = encryptionInfo;
  }

  /**
   * Elenco delle proprietà del nodo.
   **/


  @JsonProperty("properties")

  public List<Property> getProperties() {
    return properties;
  }
  public void setProperties(List<Property> properties) {
    this.properties = properties;
  }

  /**
   * Aspetti del nodo.
   **/


  @JsonProperty("aspects")

  public List<Aspect> getAspects() {
    return aspects;
  }
  public void setAspects(List<Aspect> aspects) {
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
    Node node = (Node) o;
    return Objects.equals(uid, node.uid) &&
        Objects.equals(typePrefixedName, node.typePrefixedName) &&
        Objects.equals(prefixedName, node.prefixedName) &&
        Objects.equals(modelPrefixedName, node.modelPrefixedName) &&
        Objects.equals(parentAssocTypePrefixedName, node.parentAssocTypePrefixedName) &&
        Objects.equals(contentPropertyPrefixedName, node.contentPropertyPrefixedName) &&
        Objects.equals(mimeType, node.mimeType) &&
        Objects.equals(encoding, node.encoding) &&
        Objects.equals(encryptionInfo, node.encryptionInfo) &&
        Objects.equals(properties, node.properties) &&
        Objects.equals(aspects, node.aspects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, typePrefixedName, prefixedName, modelPrefixedName, parentAssocTypePrefixedName, contentPropertyPrefixedName, mimeType, encoding, encryptionInfo, properties, aspects);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Node {\n");

    sb.append("    uid: ").append(toIndentedString(uid)).append("\n");
    sb.append("    typePrefixedName: ").append(toIndentedString(typePrefixedName)).append("\n");
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    modelPrefixedName: ").append(toIndentedString(modelPrefixedName)).append("\n");
    sb.append("    parentAssocTypePrefixedName: ").append(toIndentedString(parentAssocTypePrefixedName)).append("\n");
    sb.append("    contentPropertyPrefixedName: ").append(toIndentedString(contentPropertyPrefixedName)).append("\n");
    sb.append("    mimeType: ").append(toIndentedString(mimeType)).append("\n");
    sb.append("    encoding: ").append(toIndentedString(encoding)).append("\n");
    sb.append("    encryptionInfo: ").append(toIndentedString(encryptionInfo)).append("\n");
    sb.append("    properties: ").append(toIndentedString(properties)).append("\n");
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

