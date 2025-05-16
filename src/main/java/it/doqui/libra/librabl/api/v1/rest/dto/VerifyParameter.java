package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyParameter   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String verificationDate = null;
  private Integer verificationType = null;
  private Integer profileType = null;
  private Integer verificationScope = null;

  /**
   * Data di verifica.
   **/
  

  @JsonProperty("verificationDate") 
 
  public String getVerificationDate() {
    return verificationDate;
  }
  public void setVerificationDate(String verificationDate) {
    this.verificationDate = verificationDate;
  }

  /**
   * Tipo di verifica.
   **/
  

  @JsonProperty("verificationType") 
 
  public Integer getVerificationType() {
    return verificationType;
  }
  public void setVerificationType(Integer verificationType) {
    this.verificationType = verificationType;
  }

  /**
   * Tipo di profilo.
   **/
  

  @JsonProperty("profileType") 
 
  public Integer getProfileType() {
    return profileType;
  }
  public void setProfileType(Integer profileType) {
    this.profileType = profileType;
  }

  /**
   * Tipo di certificato.
   **/
  

  @JsonProperty("verificationScope") 
 
  public Integer getVerificationScope() {
    return verificationScope;
  }
  public void setVerificationScope(Integer verificationScope) {
    this.verificationScope = verificationScope;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VerifyParameter verifyParameter = (VerifyParameter) o;
    return Objects.equals(verificationDate, verifyParameter.verificationDate) &&
        Objects.equals(verificationType, verifyParameter.verificationType) &&
        Objects.equals(profileType, verifyParameter.profileType) &&
        Objects.equals(verificationScope, verifyParameter.verificationScope);
  }

  @Override
  public int hashCode() {
    return Objects.hash(verificationDate, verificationType, profileType, verificationScope);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VerifyParameter {\n");
    
    sb.append("    verificationDate: ").append(toIndentedString(verificationDate)).append("\n");
    sb.append("    verificationType: ").append(toIndentedString(verificationType)).append("\n");
    sb.append("    profileType: ").append(toIndentedString(profileType)).append("\n");
    sb.append("    verificationScope: ").append(toIndentedString(verificationScope)).append("\n");
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

