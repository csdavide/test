package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class ModifyAssociationRequest   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  

  /**
   * valori ammessi per action:  * COPY - Copia un nodo come figlio di un altro nodo.  * MOVE -  Sposta un nodo sotto un altro nodo.  * LINK -  Crea un link tra due nodi di un tenant.  * UNLINK - Rimuove un link tra due nodi di un tenant. 
   */
  public enum ActionEnum {
    COPY("COPY"),

        MOVE("MOVE"),

        LINK("LINK"),

        UNLINK("UNLINK");
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
   * valori ammessi per action:  * COPY - Copia un nodo come figlio di un altro nodo.  * MOVE -  Sposta un nodo sotto un altro nodo.  * LINK -  Crea un link tra due nodi di un tenant.  * UNLINK - Rimuove un link tra due nodi di un tenant. 
   **/
  

  @JsonProperty("action") 
 
  public ActionEnum getAction() {
    return action;
  }
  public void setAction(ActionEnum action) {
    this.action = action;
  }

  /**
   * UUID del nodo target che, a seconda dell&#39;operazione, rappresenta :  * COPY - Il nodo padre di destinazione.  * MOVE -  Il nuovo nodo padre.  * LINK - il nodo destinazione (figlio).  * UNLINK - il nodo destinazione (figlio). 
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
    ModifyAssociationRequest modifyAssociationRequest = (ModifyAssociationRequest) o;
    return Objects.equals(action, modifyAssociationRequest.action) &&
        Objects.equals(targetUid, modifyAssociationRequest.targetUid) &&
        Objects.equals(association, modifyAssociationRequest.association);
  }

  @Override
  public int hashCode() {
    return Objects.hash(action, targetUid, association);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ModifyAssociationRequest {\n");
    
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

