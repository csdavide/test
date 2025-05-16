package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AsyncSigillo   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String status = null;
  private String signedUid = null;

  /**
   * Stato del report, che può essere READY, SCHEDULED, EXPIRED, ERROR.
   **/
  

  @JsonProperty("status") 
 
  public String getStatus() {
    return status;
  }
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * uid del file sigillato salvato nell&#39;area temporanea
   **/
  

  @JsonProperty("signedUid") 
 
  public String getSignedUid() {
    return signedUid;
  }
  public void setSignedUid(String signedUid) {
    this.signedUid = signedUid;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AsyncSigillo asyncSigillo = (AsyncSigillo) o;
    return Objects.equals(status, asyncSigillo.status) &&
        Objects.equals(signedUid, asyncSigillo.signedUid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, signedUid);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AsyncSigillo {\n");
    
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    signedUid: ").append(toIndentedString(signedUid)).append("\n");
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

