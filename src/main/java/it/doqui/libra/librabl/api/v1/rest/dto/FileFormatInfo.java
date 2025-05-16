package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FileFormatInfo   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String puid = null;
  private String mimeType = null;
  private String description = null;
  private String formatVersion = null;
  private Integer typeCode = null;
  private String typeDescription = null;
  private String warning = null;
  private String identificationDate = null;
  private String uid = null;
  private String typeExtension = null;

  /**
   * PUID associato al tipo di file riconosciuto.
   **/
  

  @JsonProperty("puid") 
 
  public String getPuid() {
    return puid;
  }
  public void setPuid(String puid) {
    this.puid = puid;
  }

  /**
   * MIME type.
   **/
  

  @JsonProperty("mimeType") 
 
  public String getMimeType() {
    return mimeType;
  }
  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  /**
   * Esito del processo di identificazione.
   **/
  

  @JsonProperty("description") 
 
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Versione del formato riconosciuto.
   **/
  

  @JsonProperty("formatVersion") 
 
  public String getFormatVersion() {
    return formatVersion;
  }
  public void setFormatVersion(String formatVersion) {
    this.formatVersion = formatVersion;
  }

  /**
   * Codice di tipo associato al formato.
   **/
  

  @JsonProperty("typeCode") 
 
  public Integer getTypeCode() {
    return typeCode;
  }
  public void setTypeCode(Integer typeCode) {
    this.typeCode = typeCode;
  }

  /**
   * Esito del processo di identificazione.
   **/
  

  @JsonProperty("typeDescription") 
 
  public String getTypeDescription() {
    return typeDescription;
  }
  public void setTypeDescription(String typeDescription) {
    this.typeDescription = typeDescription;
  }

  /**
   * Eventuali informazioni aggiuntive sul riconoscimento.
   **/
  

  @JsonProperty("warning") 
 
  public String getWarning() {
    return warning;
  }
  public void setWarning(String warning) {
    this.warning = warning;
  }

  /**
   * Timestamp del riconoscimento.
   **/
  

  @JsonProperty("identificationDate") 
 
  public String getIdentificationDate() {
    return identificationDate;
  }
  public void setIdentificationDate(String identificationDate) {
    this.identificationDate = identificationDate;
  }

  /**
   * Eventuale UID del nodo associato al riconoscimento.
   **/
  

  @JsonProperty("uid") 
 
  public String getUid() {
    return uid;
  }
  public void setUid(String uid) {
    this.uid = uid;
  }

  /**
   * Estensione del formato riconosciuto.
   **/
  

  @JsonProperty("typeExtension") 
 
  public String getTypeExtension() {
    return typeExtension;
  }
  public void setTypeExtension(String typeExtension) {
    this.typeExtension = typeExtension;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileFormatInfo fileFormatInfo = (FileFormatInfo) o;
    return Objects.equals(puid, fileFormatInfo.puid) &&
        Objects.equals(mimeType, fileFormatInfo.mimeType) &&
        Objects.equals(description, fileFormatInfo.description) &&
        Objects.equals(formatVersion, fileFormatInfo.formatVersion) &&
        Objects.equals(typeCode, fileFormatInfo.typeCode) &&
        Objects.equals(typeDescription, fileFormatInfo.typeDescription) &&
        Objects.equals(warning, fileFormatInfo.warning) &&
        Objects.equals(identificationDate, fileFormatInfo.identificationDate) &&
        Objects.equals(uid, fileFormatInfo.uid) &&
        Objects.equals(typeExtension, fileFormatInfo.typeExtension);
  }

  @Override
  public int hashCode() {
    return Objects.hash(puid, mimeType, description, formatVersion, typeCode, typeDescription, warning, identificationDate, uid, typeExtension);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class FileFormatInfo {\n");
    
    sb.append("    puid: ").append(toIndentedString(puid)).append("\n");
    sb.append("    mimeType: ").append(toIndentedString(mimeType)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    formatVersion: ").append(toIndentedString(formatVersion)).append("\n");
    sb.append("    typeCode: ").append(toIndentedString(typeCode)).append("\n");
    sb.append("    typeDescription: ").append(toIndentedString(typeDescription)).append("\n");
    sb.append("    warning: ").append(toIndentedString(warning)).append("\n");
    sb.append("    identificationDate: ").append(toIndentedString(identificationDate)).append("\n");
    sb.append("    uid: ").append(toIndentedString(uid)).append("\n");
    sb.append("    typeExtension: ").append(toIndentedString(typeExtension)).append("\n");
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

