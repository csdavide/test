package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CompareDigestRequest   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private NodeInfo nodeInfo = null;
  private NodeInfo tempNodeInfo = null;

  /**
   **/
  

  @JsonProperty("nodeInfo") 
 
  public NodeInfo getNodeInfo() {
    return nodeInfo;
  }
  public void setNodeInfo(NodeInfo nodeInfo) {
    this.nodeInfo = nodeInfo;
  }

  /**
   **/
  

  @JsonProperty("tempNodeInfo") 
 
  public NodeInfo getTempNodeInfo() {
    return tempNodeInfo;
  }
  public void setTempNodeInfo(NodeInfo tempNodeInfo) {
    this.tempNodeInfo = tempNodeInfo;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CompareDigestRequest compareDigestRequest = (CompareDigestRequest) o;
    return Objects.equals(nodeInfo, compareDigestRequest.nodeInfo) &&
        Objects.equals(tempNodeInfo, compareDigestRequest.tempNodeInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeInfo, tempNodeInfo);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CompareDigestRequest {\n");
    
    sb.append("    nodeInfo: ").append(toIndentedString(nodeInfo)).append("\n");
    sb.append("    tempNodeInfo: ").append(toIndentedString(tempNodeInfo)).append("\n");
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

