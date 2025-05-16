package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyReportExt   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String tokenUuid = null;
  private Integer signCount = null;
  private VerifyReport report = null;

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

  /**
   * Numero di firme.
   **/
  

  @JsonProperty("signCount") 
 
  public Integer getSignCount() {
    return signCount;
  }
  public void setSignCount(Integer signCount) {
    this.signCount = signCount;
  }

  /**
   **/
  

  @JsonProperty("report") 
 
  public VerifyReport getReport() {
    return report;
  }
  public void setReport(VerifyReport report) {
    this.report = report;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VerifyReportExt verifyReportExt = (VerifyReportExt) o;
    return Objects.equals(tokenUuid, verifyReportExt.tokenUuid) &&
        Objects.equals(signCount, verifyReportExt.signCount) &&
        Objects.equals(report, verifyReportExt.report);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tokenUuid, signCount, report);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VerifyReportExt {\n");
    
    sb.append("    tokenUuid: ").append(toIndentedString(tokenUuid)).append("\n");
    sb.append("    signCount: ").append(toIndentedString(signCount)).append("\n");
    sb.append("    report: ").append(toIndentedString(report)).append("\n");
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

