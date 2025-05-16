package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DestinationInfo   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String parentDestinationUid = null;
  private OperationContext operationContext = null;

  /**
   * UID del nodo padre di destinazione della copia.
   **/
  

  @JsonProperty("parentDestinationUid") 
 
  public String getParentDestinationUid() {
    return parentDestinationUid;
  }
  public void setParentDestinationUid(String parentDestinationUid) {
    this.parentDestinationUid = parentDestinationUid;
  }

  /**
   **/
  

  @JsonProperty("operationContext") 
 
  public OperationContext getOperationContext() {
    return operationContext;
  }
  public void setOperationContext(OperationContext operationContext) {
    this.operationContext = operationContext;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DestinationInfo destinationInfo = (DestinationInfo) o;
    return Objects.equals(parentDestinationUid, destinationInfo.parentDestinationUid) &&
        Objects.equals(operationContext, destinationInfo.operationContext);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parentDestinationUid, operationContext);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DestinationInfo {\n");
    
    sb.append("    parentDestinationUid: ").append(toIndentedString(parentDestinationUid)).append("\n");
    sb.append("    operationContext: ").append(toIndentedString(operationContext)).append("\n");
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

