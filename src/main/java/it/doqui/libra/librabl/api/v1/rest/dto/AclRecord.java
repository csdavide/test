package it.doqui.libra.librabl.api.v1.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class AclRecord   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String authority = null;
  private String permission = null;
  private Boolean accessAllowed = null;

  /**
   * Rappresenta l&#39;autority.
   **/
  

  @JsonProperty("authority") 
 
  public String getAuthority() {
    return authority;
  }
  public void setAuthority(String authority) {
    this.authority = authority;
  }

  /**
   * Rappresenta il permesso.
   **/
  

  @JsonProperty("permission") 
 
  public String getPermission() {
    return permission;
  }
  public void setPermission(String permission) {
    this.permission = permission;
  }

  /**
   * Comportamento permissivo o remissivo dell&#39;ACL.
   **/
  

  @JsonProperty("accessAllowed") 
 
  public Boolean isAccessAllowed() {
    return accessAllowed;
  }
  public void setAccessAllowed(Boolean accessAllowed) {
    this.accessAllowed = accessAllowed;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AclRecord aclRecord = (AclRecord) o;
    return Objects.equals(authority, aclRecord.authority) &&
        Objects.equals(permission, aclRecord.permission) &&
        Objects.equals(accessAllowed, aclRecord.accessAllowed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authority, permission, accessAllowed);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AclRecord {\n");
    
    sb.append("    authority: ").append(toIndentedString(authority)).append("\n");
    sb.append("    permission: ").append(toIndentedString(permission)).append("\n");
    sb.append("    accessAllowed: ").append(toIndentedString(accessAllowed)).append("\n");
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

