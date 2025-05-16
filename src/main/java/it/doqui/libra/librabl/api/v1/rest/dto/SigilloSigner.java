package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class SigilloSigner   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  

  /**
   * Tipo di sigillo elettronico.
   */
  public enum TypeEnum {
    PDF("PDF"),

        XMLENVELOPED("XMLENVELOPED"),

        XMLENVELOPING("XMLENVELOPING"),

        XMLDETACHED_INTERNAL("XMLDETACHED_INTERNAL");
    private String value;

    TypeEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return String.valueOf(value);
    }
  }

  private TypeEnum type = null;
  private String delegatedDomain = null;
  private String delegatedPassword = null;
  private String delegatedUser = null;
  private String user = null;
  private String otpPwd = null;
  private String typeHSM = null;
  private String typeOtpAuth = null;

  /**
   * Tipo di sigillo elettronico.
   **/
  

  @JsonProperty("type") 
 
  public TypeEnum getType() {
    return type;
  }
  public void setType(TypeEnum type) {
    this.type = type;
  }

  /**
   * Dominio dell’utente delegato.
   **/
  

  @JsonProperty("delegatedDomain") 
 
  public String getDelegatedDomain() {
    return delegatedDomain;
  }
  public void setDelegatedDomain(String delegatedDomain) {
    this.delegatedDomain = delegatedDomain;
  }

  /**
   * Password dell’utente delegato.
   **/
  

  @JsonProperty("delegatedPassword") 
 
  public String getDelegatedPassword() {
    return delegatedPassword;
  }
  public void setDelegatedPassword(String delegatedPassword) {
    this.delegatedPassword = delegatedPassword;
  }

  /**
   * User dell’utente delegato.
   **/
  

  @JsonProperty("delegatedUser") 
 
  public String getDelegatedUser() {
    return delegatedUser;
  }
  public void setDelegatedUser(String delegatedUser) {
    this.delegatedUser = delegatedUser;
  }

  /**
   * CF del delegante.
   **/
  

  @JsonProperty("user") 
 
  public String getUser() {
    return user;
  }
  public void setUser(String user) {
    this.user = user;
  }

  /**
   * codice OTP dell’utente valido per la transazione di firma.
   **/
  

  @JsonProperty("otpPwd") 
 
  public String getOtpPwd() {
    return otpPwd;
  }
  public void setOtpPwd(String otpPwd) {
    this.otpPwd = otpPwd;
  }

  /**
   * Valorizzare con COSIGN.
   **/
  

  @JsonProperty("typeHSM") 
 
  public String getTypeHSM() {
    return typeHSM;
  }
  public void setTypeHSM(String typeHSM) {
    this.typeHSM = typeHSM;
  }

  /**
   * Dominio di autenticazione.
   **/
  

  @JsonProperty("typeOtpAuth") 
 
  public String getTypeOtpAuth() {
    return typeOtpAuth;
  }
  public void setTypeOtpAuth(String typeOtpAuth) {
    this.typeOtpAuth = typeOtpAuth;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SigilloSigner sigilloSigner = (SigilloSigner) o;
    return Objects.equals(type, sigilloSigner.type) &&
        Objects.equals(delegatedDomain, sigilloSigner.delegatedDomain) &&
        Objects.equals(delegatedPassword, sigilloSigner.delegatedPassword) &&
        Objects.equals(delegatedUser, sigilloSigner.delegatedUser) &&
        Objects.equals(user, sigilloSigner.user) &&
        Objects.equals(otpPwd, sigilloSigner.otpPwd) &&
        Objects.equals(typeHSM, sigilloSigner.typeHSM) &&
        Objects.equals(typeOtpAuth, sigilloSigner.typeOtpAuth);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, delegatedDomain, delegatedPassword, delegatedUser, user, otpPwd, typeHSM, typeOtpAuth);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SigilloSigner {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    delegatedDomain: ").append(toIndentedString(delegatedDomain)).append("\n");
    sb.append("    delegatedPassword: ").append(toIndentedString(delegatedPassword)).append("\n");
    sb.append("    delegatedUser: ").append(toIndentedString(delegatedUser)).append("\n");
    sb.append("    user: ").append(toIndentedString(user)).append("\n");
    sb.append("    otpPwd: ").append(toIndentedString(otpPwd)).append("\n");
    sb.append("    typeHSM: ").append(toIndentedString(typeHSM)).append("\n");
    sb.append("    typeOtpAuth: ").append(toIndentedString(typeOtpAuth)).append("\n");
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

