package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ResultAssociation   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String targetUid = null;
  private String prefixedName = null;
  private String typePrefixedName = null;
  private Boolean childAssociation = null;

  /**
   * UID del nodo figlio o destinazione.
   **/
  

  @JsonProperty("targetUid") 
 
  public String getTargetUid() {
    return targetUid;
  }
  public void setTargetUid(String targetUid) {
    this.targetUid = targetUid;
  }

  /**
   * Prefixed name del nome dell&#39;associazione.
   **/
  

  @JsonProperty("prefixedName") 
 
  public String getPrefixedName() {
    return prefixedName;
  }
  public void setPrefixedName(String prefixedName) {
    this.prefixedName = prefixedName;
  }

  /**
   * Prefixed name del tipo dell&#39;associazione.
   **/
  

  @JsonProperty("typePrefixedName") 
 
  public String getTypePrefixedName() {
    return typePrefixedName;
  }
  public void setTypePrefixedName(String typePrefixedName) {
    this.typePrefixedName = typePrefixedName;
  }

  /**
   * Indica se l&#39;associazione è di tipo padre/figlio o di tipo sorgente/destinazione.
   **/
  

  @JsonProperty("childAssociation") 
 
  public Boolean isChildAssociation() {
    return childAssociation;
  }
  public void setChildAssociation(Boolean childAssociation) {
    this.childAssociation = childAssociation;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ResultAssociation resultAssociation = (ResultAssociation) o;
    return Objects.equals(targetUid, resultAssociation.targetUid) &&
        Objects.equals(prefixedName, resultAssociation.prefixedName) &&
        Objects.equals(typePrefixedName, resultAssociation.typePrefixedName) &&
        Objects.equals(childAssociation, resultAssociation.childAssociation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetUid, prefixedName, typePrefixedName, childAssociation);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ResultAssociation {\n");
    
    sb.append("    targetUid: ").append(toIndentedString(targetUid)).append("\n");
    sb.append("    prefixedName: ").append(toIndentedString(prefixedName)).append("\n");
    sb.append("    typePrefixedName: ").append(toIndentedString(typePrefixedName)).append("\n");
    sb.append("    childAssociation: ").append(toIndentedString(childAssociation)).append("\n");
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

