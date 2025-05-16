package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class ModifyAssociationJobRequest   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  

  /**
   * valori ammessi per action:  * moveJob - Effettua una richiesta di spostamento di un nodo asincrona.  * linkJob - Effettua una richiesta di link di un nodo asincrona. 
   */
  public enum ActionEnum {
    MOVEJOB("moveJob"),

        LINKJOB("linkJob");
    private String value;

    ActionEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return String.valueOf(value);
    }
  }

  private ActionEnum action = null;
  private String targetUid = null;
  private Association association = null;

  /**
   * valori ammessi per action:  * moveJob - Effettua una richiesta di spostamento di un nodo asincrona.  * linkJob - Effettua una richiesta di link di un nodo asincrona. 
   **/
  

  @JsonProperty("action") 
 
  public ActionEnum getAction() {
    return action;
  }
  public void setAction(ActionEnum action) {
    this.action = action;
  }

  /**
   * UUID del nodo target che, a seconda dell&#39;operazione, rappresenta :  * moveJob - Il nuovo nodo padre.  * linkJob - Il nodo destinazione (figlio). 
   **/
  

  @JsonProperty("targetUid") 
 
  public String getTargetUid() {
    return targetUid;
  }
  public void setTargetUid(String targetUid) {
    this.targetUid = targetUid;
  }

  /**
   **/
  

  @JsonProperty("association") 
 
  public Association getAssociation() {
    return association;
  }
  public void setAssociation(Association association) {
    this.association = association;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ModifyAssociationJobRequest modifyAssociationJobRequest = (ModifyAssociationJobRequest) o;
    return Objects.equals(action, modifyAssociationJobRequest.action) &&
        Objects.equals(targetUid, modifyAssociationJobRequest.targetUid) &&
        Objects.equals(association, modifyAssociationJobRequest.association);
  }

  @Override
  public int hashCode() {
    return Objects.hash(action, targetUid, association);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ModifyAssociationJobRequest {\n");
    
    sb.append("    action: ").append(toIndentedString(action)).append("\n");
    sb.append("    targetUid: ").append(toIndentedString(targetUid)).append("\n");
    sb.append("    association: ").append(toIndentedString(association)).append("\n");
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

