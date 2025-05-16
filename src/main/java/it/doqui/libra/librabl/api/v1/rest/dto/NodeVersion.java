package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeVersion   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String creator = null;
  private String createdDate = null;
  private String description = null;
  private String versionLabel = null;
  private String versionedNodeUid = null;
  private List<Property> versionProperties = new ArrayList<Property>();

  /**
   * Nome utente del creatore della versione.
   **/
  

  @JsonProperty("creator") 
 
  public String getCreator() {
    return creator;
  }
  public void setCreator(String creator) {
    this.creator = creator;
  }

  /**
   * Data di creazione della versione.
   **/
  

  @JsonProperty("createdDate") 
 
  public String getCreatedDate() {
    return createdDate;
  }
  public void setCreatedDate(String createdDate) {
    this.createdDate = createdDate;
  }

  /**
   * Descrizione della versione.
   **/
  

  @JsonProperty("description") 
 
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Numero di versione.
   **/
  

  @JsonProperty("versionLabel") 
 
  public String getVersionLabel() {
    return versionLabel;
  }
  public void setVersionLabel(String versionLabel) {
    this.versionLabel = versionLabel;
  }

  /**
   * UID del nodo da cui la versione è stata creata.
   **/
  

  @JsonProperty("versionedNodeUid") 
 
  public String getVersionedNodeUid() {
    return versionedNodeUid;
  }
  public void setVersionedNodeUid(String versionedNodeUid) {
    this.versionedNodeUid = versionedNodeUid;
  }

  /**
   * Proprietà della versione.
   **/
  

  @JsonProperty("versionProperties") 
 
  public List<Property> getVersionProperties() {
    return versionProperties;
  }
  public void setVersionProperties(List<Property> versionProperties) {
    this.versionProperties = versionProperties;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodeVersion nodeVersion = (NodeVersion) o;
    return Objects.equals(creator, nodeVersion.creator) &&
        Objects.equals(createdDate, nodeVersion.createdDate) &&
        Objects.equals(description, nodeVersion.description) &&
        Objects.equals(versionLabel, nodeVersion.versionLabel) &&
        Objects.equals(versionedNodeUid, nodeVersion.versionedNodeUid) &&
        Objects.equals(versionProperties, nodeVersion.versionProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(creator, createdDate, description, versionLabel, versionedNodeUid, versionProperties);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class NodeVersion {\n");
    
    sb.append("    creator: ").append(toIndentedString(creator)).append("\n");
    sb.append("    createdDate: ").append(toIndentedString(createdDate)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    versionLabel: ").append(toIndentedString(versionLabel)).append("\n");
    sb.append("    versionedNodeUid: ").append(toIndentedString(versionedNodeUid)).append("\n");
    sb.append("    versionProperties: ").append(toIndentedString(versionProperties)).append("\n");
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

