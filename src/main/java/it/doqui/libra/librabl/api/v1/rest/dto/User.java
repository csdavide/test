package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class User  {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String name = null;
  private String surname = null;
  private String username = null;
  private String password = null;
  private String homeFolderPath = null;

  /**
   * Nome anagrafico dell&#39;utente.
   **/
  

  @JsonProperty("name") 
 
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Cognome anagrafico dell&#39;utente.
   **/
  

  @JsonProperty("surname") 
 
  public String getSurname() {
    return surname;
  }
  public void setSurname(String surname) {
    this.surname = surname;
  }

  /**
   * Nome utente applicativo.
   **/
  

  @JsonProperty("username") 
 
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Password dell&#39;utente.
   **/
  

  @JsonProperty("password") 
 
  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * Percorso della user home dell&#39;utente.
   **/
  

  @JsonProperty("homeFolderPath") 
 
  public String getHomeFolderPath() {
    return homeFolderPath;
  }
  public void setHomeFolderPath(String homeFolderPath) {
    this.homeFolderPath = homeFolderPath;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    User user = (User) o;
    return Objects.equals(name, user.name) &&
        Objects.equals(surname, user.surname) &&
        Objects.equals(username, user.username) &&
        Objects.equals(password, user.password) &&
        Objects.equals(homeFolderPath, user.homeFolderPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, surname, username, password, homeFolderPath);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class User {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    surname: ").append(toIndentedString(surname)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("    homeFolderPath: ").append(toIndentedString(homeFolderPath)).append("\n");
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
