package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SigilloSignedExt   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String uid = null;
  private String tokenUuid = null;

  /**
   * UUID del documento sigillato salvato nell&#39;area temporanea.
   **/
  

  @JsonProperty("uid") 
 
  public String getUid() {
    return uid;
  }
  public void setUid(String uid) {
    this.uid = uid;
  }

  /**
   * Token Uuid dell&#39;elaborazione asincrona.
   **/
  

  @JsonProperty("tokenUuid") 
 
  public String getTokenUuid() {
    return tokenUuid;
  }
  public void setTokenUuid(String tokenUuid) {
    this.tokenUuid = tokenUuid;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SigilloSignedExt sigilloSignedExt = (SigilloSignedExt) o;
    return Objects.equals(uid, sigilloSignedExt.uid) &&
        Objects.equals(tokenUuid, sigilloSignedExt.tokenUuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, tokenUuid);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SigilloSignedExt {\n");
    
    sb.append("    uid: ").append(toIndentedString(uid)).append("\n");
    sb.append("    tokenUuid: ").append(toIndentedString(tokenUuid)).append("\n");
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

