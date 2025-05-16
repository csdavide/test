package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyReport   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private VerifyReport child = null;
  private Integer conformitaParametriInput = null;
  private String date = null;
  private Integer errorCode = null;
  private Integer formatoFirma = null;
  private Integer numCertificatiFirma = null;
  private Integer numCertificatiMarca = null;
  private List<Signature> signature = new ArrayList<Signature>();
  private Integer tipoFirma = null;
  private List<String> uid = new ArrayList<String>();

  /**
   **/
  

  @JsonProperty("child") 
 
  public VerifyReport getChild() {
    return child;
  }
  public void setChild(VerifyReport child) {
    this.child = child;
  }

  /**
   * Conformita dei parametri di input:  0 NON OK 1 OK 
   **/
  

  @JsonProperty("conformitaParametriInput") 
 
  public Integer getConformitaParametriInput() {
    return conformitaParametriInput;
  }
  public void setConformitaParametriInput(Integer conformitaParametriInput) {
    this.conformitaParametriInput = conformitaParametriInput;
  }

  /**
   * Data in cui è stato chiesto il rapporto di verifica.
   **/
  

  @JsonProperty("date") 
 
  public String getDate() {
    return date;
  }
  public void setDate(String date) {
    this.date = date;
  }

  /**
   * Eventuale codice di errore : viene restituito  0 se il processo si è concluso correttamente  altrimenti un valore da 1 a 7 in caso di fallimento di uno dei sette passi.    &gt;      1: Verifica conformità e integrità busta crittografica.      2: Sbustamento busta crittografica.      3: Verifica consistenza firma.      4: Verifica validità certificato.      5: Verifica certification authority.      6: Verifica lista di revoca — CRL aggiornata non disponibile.      7: Verifica lista di revoca — certificato presente nella CRL. 
   **/
  

  @JsonProperty("errorCode") 
 
  public Integer getErrorCode() {
    return errorCode;
  }
  public void setErrorCode(Integer errorCode) {
    this.errorCode = errorCode;
  }

  /**
   * Formato della firma:  1 in caso di firma enveloped 2 in caso di firma separata 3 in caso di marca temporale InfoCert  4 in caso di marca temporale separata 
   **/
  

  @JsonProperty("formatoFirma") 
 
  public Integer getFormatoFirma() {
    return formatoFirma;
  }
  public void setFormatoFirma(Integer formatoFirma) {
    this.formatoFirma = formatoFirma;
  }

  /**
   * Numero dei certificati della firma.
   **/
  

  @JsonProperty("numCertificatiFirma") 
 
  public Integer getNumCertificatiFirma() {
    return numCertificatiFirma;
  }
  public void setNumCertificatiFirma(Integer numCertificatiFirma) {
    this.numCertificatiFirma = numCertificatiFirma;
  }

  /**
   * Numero dei certificati della marca temporale.
   **/
  

  @JsonProperty("numCertificatiMarca") 
 
  public Integer getNumCertificatiMarca() {
    return numCertificatiMarca;
  }
  public void setNumCertificatiMarca(Integer numCertificatiMarca) {
    this.numCertificatiMarca = numCertificatiMarca;
  }

  /**
   * Firme del file.
   **/
  

  @JsonProperty("signature") 
 
  public List<Signature> getSignature() {
    return signature;
  }
  public void setSignature(List<Signature> signature) {
    this.signature = signature;
  }

  /**
   * Tipologia di firma o marca temporale:  0 in caso di marca temporale 1 in caso di firma semplice 2 in caso di firma multipla parallela  3 in caso di firma multipla controfirma 4 in caso di firma multipla a catena 
   **/
  

  @JsonProperty("tipoFirma") 
 
  public Integer getTipoFirma() {
    return tipoFirma;
  }
  public void setTipoFirma(Integer tipoFirma) {
    this.tipoFirma = tipoFirma;
  }

  /**
   * Eventuale UID del file.
   **/
  

  @JsonProperty("uid") 
 
  public List<String> getUid() {
    return uid;
  }
  public void setUid(List<String> uid) {
    this.uid = uid;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VerifyReport verifyReport = (VerifyReport) o;
    return Objects.equals(child, verifyReport.child) &&
        Objects.equals(conformitaParametriInput, verifyReport.conformitaParametriInput) &&
        Objects.equals(date, verifyReport.date) &&
        Objects.equals(errorCode, verifyReport.errorCode) &&
        Objects.equals(formatoFirma, verifyReport.formatoFirma) &&
        Objects.equals(numCertificatiFirma, verifyReport.numCertificatiFirma) &&
        Objects.equals(numCertificatiMarca, verifyReport.numCertificatiMarca) &&
        Objects.equals(signature, verifyReport.signature) &&
        Objects.equals(tipoFirma, verifyReport.tipoFirma) &&
        Objects.equals(uid, verifyReport.uid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(child, conformitaParametriInput, date, errorCode, formatoFirma, numCertificatiFirma, numCertificatiMarca, signature, tipoFirma, uid);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VerifyReport {\n");
    
    sb.append("    child: ").append(toIndentedString(child)).append("\n");
    sb.append("    conformitaParametriInput: ").append(toIndentedString(conformitaParametriInput)).append("\n");
    sb.append("    date: ").append(toIndentedString(date)).append("\n");
    sb.append("    errorCode: ").append(toIndentedString(errorCode)).append("\n");
    sb.append("    formatoFirma: ").append(toIndentedString(formatoFirma)).append("\n");
    sb.append("    numCertificatiFirma: ").append(toIndentedString(numCertificatiFirma)).append("\n");
    sb.append("    numCertificatiMarca: ").append(toIndentedString(numCertificatiMarca)).append("\n");
    sb.append("    signature: ").append(toIndentedString(signature)).append("\n");
    sb.append("    tipoFirma: ").append(toIndentedString(tipoFirma)).append("\n");
    sb.append("    uid: ").append(toIndentedString(uid)).append("\n");
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

