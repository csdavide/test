package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MassiveDeleteNodeRequest   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private List<String> uids = new ArrayList<String>();
  private MassiveDeleteNodeAction action = null;

  /**
   * Elenco degli UID dei nodi da cancellare
   **/
  

  @JsonProperty("uids") 
 
  public List<String> getUids() {
    return uids;
  }
  public void setUids(List<String> uids) {
    this.uids = uids;
  }

  /**
   **/
  

  @JsonProperty("action") 
 
  public MassiveDeleteNodeAction getAction() {
    return action;
  }
  public void setAction(MassiveDeleteNodeAction action) {
    this.action = action;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MassiveDeleteNodeRequest massiveDeleteNodeRequest = (MassiveDeleteNodeRequest) o;
    return Objects.equals(uids, massiveDeleteNodeRequest.uids) &&
        Objects.equals(action, massiveDeleteNodeRequest.action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uids, action);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MassiveDeleteNodeRequest {\n");
    
    sb.append("    uids: ").append(toIndentedString(uids)).append("\n");
    sb.append("    action: ").append(toIndentedString(action)).append("\n");
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

