package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EncryptionInfo   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String key = null;
  private String algorithm = null;
  private String padding = null;
  private String mode = null;
  private String keyId = null;
  private String sourceIV = null;
  private Boolean sourceEncrypted = null;

  /**
   * Chiave di cifratura.
   **/
  

  @JsonProperty("key") 
 
  public String getKey() {
    return key;
  }
  public void setKey(String key) {
    this.key = key;
  }

  /**
   * Algoritmo utilizzato per la cifratura.
   **/
  

  @JsonProperty("algorithm") 
 
  public String getAlgorithm() {
    return algorithm;
  }
  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  /**
   * Tipo di padding utilizzato per la cifratura.
   **/
  

  @JsonProperty("padding") 
 
  public String getPadding() {
    return padding;
  }
  public void setPadding(String padding) {
    this.padding = padding;
  }

  /**
   * Modalità di cifratura.
   **/
  

  @JsonProperty("mode") 
 
  public String getMode() {
    return mode;
  }
  public void setMode(String mode) {
    this.mode = mode;
  }

  /**
   * Identificativo della chiave crittografica utilizzata.
   **/
  

  @JsonProperty("keyId") 
 
  public String getKeyId() {
    return keyId;
  }
  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  /**
   * Initialization Vector utilizzato per criptare il contenuto alla fonte.
   **/
  

  @JsonProperty("sourceIV") 
 
  public String getSourceIV() {
    return sourceIV;
  }
  public void setSourceIV(String sourceIV) {
    this.sourceIV = sourceIV;
  }

  /**
   * Indica se il contenuto è criptato alla fonte.
   **/
  

  @JsonProperty("sourceEncrypted") 
 
  public Boolean isSourceEncrypted() {
    return sourceEncrypted;
  }
  public void setSourceEncrypted(Boolean sourceEncrypted) {
    this.sourceEncrypted = sourceEncrypted;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EncryptionInfo encryptionInfo = (EncryptionInfo) o;
    return Objects.equals(key, encryptionInfo.key) &&
        Objects.equals(algorithm, encryptionInfo.algorithm) &&
        Objects.equals(padding, encryptionInfo.padding) &&
        Objects.equals(mode, encryptionInfo.mode) &&
        Objects.equals(keyId, encryptionInfo.keyId) &&
        Objects.equals(sourceIV, encryptionInfo.sourceIV) &&
        Objects.equals(sourceEncrypted, encryptionInfo.sourceEncrypted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, algorithm, padding, mode, keyId, sourceIV, sourceEncrypted);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EncryptionInfo {\n");
    
    sb.append("    key: ").append(toIndentedString(key)).append("\n");
    sb.append("    algorithm: ").append(toIndentedString(algorithm)).append("\n");
    sb.append("    padding: ").append(toIndentedString(padding)).append("\n");
    sb.append("    mode: ").append(toIndentedString(mode)).append("\n");
    sb.append("    keyId: ").append(toIndentedString(keyId)).append("\n");
    sb.append("    sourceIV: ").append(toIndentedString(sourceIV)).append("\n");
    sb.append("    sourceEncrypted: ").append(toIndentedString(sourceEncrypted)).append("\n");
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

