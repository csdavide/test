package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Job   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String name = null;
  private String status = null;
  private String created = null;
  private String updated = null;
  private String message = null;

  /**
   * Nome del job.
   **/
  

  @JsonProperty("name") 
 
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Stato del job, che può essere READY, RUNNING, FINISHED, ERROR.
   **/
  

  @JsonProperty("status") 
 
  public String getStatus() {
    return status;
  }
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Data di creazione del job.
   **/
  

  @JsonProperty("created") 
 
  public String getCreated() {
    return created;
  }
  public void setCreated(String created) {
    this.created = created;
  }

  /**
   * Data di ultimo aggiornamento del job.
   **/
  

  @JsonProperty("updated") 
 
  public String getUpdated() {
    return updated;
  }
  public void setUpdated(String updated) {
    this.updated = updated;
  }

  /**
   * Messaggio del job, valorizzato specialmente in caso di errore.
   **/
  

  @JsonProperty("message") 
 
  public String getMessage() {
    return message;
  }
  public void setMessage(String message) {
    this.message = message;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Job job = (Job) o;
    return Objects.equals(name, job.name) &&
        Objects.equals(status, job.status) &&
        Objects.equals(created, job.created) &&
        Objects.equals(updated, job.updated) &&
        Objects.equals(message, job.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, status, created, updated, message);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Job {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    created: ").append(toIndentedString(created)).append("\n");
    sb.append("    updated: ").append(toIndentedString(updated)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
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

