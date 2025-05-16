package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OperationContext   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String username = null;
  private String password = null;
  private String repository = null;
  private String tenant = null;
  private String fruitore = null;
  private String nomeFisico = null;

  /**
   * Username dell&#39;utente applicativo.
   **/
  

  @JsonProperty("username") 
 
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Password dell&#39;utente applicativo.
   **/
  

  @JsonProperty("password") 
 
  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * Repository fisico (primary o secondary).
   **/
  

  @JsonProperty("repository") 
 
  public String getRepository() {
    return repository;
  }
  public void setRepository(String repository) {
    this.repository = repository;
  }

  /**
   * Tenant di destinazione.
   **/
  

  @JsonProperty("tenant") 
 
  public String getTenant() {
    return tenant;
  }
  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  /**
   **/
  

  @JsonProperty("fruitore") 
 
  public String getFruitore() {
    return fruitore;
  }
  public void setFruitore(String fruitore) {
    this.fruitore = fruitore;
  }

  /**
   **/
  

  @JsonProperty("nomeFisico") 
 
  public String getNomeFisico() {
    return nomeFisico;
  }
  public void setNomeFisico(String nomeFisico) {
    this.nomeFisico = nomeFisico;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OperationContext operationContext = (OperationContext) o;
    return Objects.equals(username, operationContext.username) &&
        Objects.equals(password, operationContext.password) &&
        Objects.equals(repository, operationContext.repository) &&
        Objects.equals(tenant, operationContext.tenant) &&
        Objects.equals(fruitore, operationContext.fruitore) &&
        Objects.equals(nomeFisico, operationContext.nomeFisico);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password, repository, tenant, fruitore, nomeFisico);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OperationContext {\n");
    
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("    repository: ").append(toIndentedString(repository)).append("\n");
    sb.append("    tenant: ").append(toIndentedString(tenant)).append("\n");
    sb.append("    fruitore: ").append(toIndentedString(fruitore)).append("\n");
    sb.append("    nomeFisico: ").append(toIndentedString(nomeFisico)).append("\n");
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

