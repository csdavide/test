package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AsyncReport   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String status = null;
  private VerifyReport report = null;

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
    AsyncReport asyncReport = (AsyncReport) o;
    return Objects.equals(status, asyncReport.status) &&
        Objects.equals(report, asyncReport.report);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, report);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AsyncReport {\n");
    
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
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

