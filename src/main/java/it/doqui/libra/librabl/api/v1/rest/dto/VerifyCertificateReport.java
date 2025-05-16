package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyCertificateReport   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String verificationResult = null;
  private Integer errorCode = null;
  private String errorDescription = null;
  private String certificationAuthority = null;
  private String serialNumber = null;
  private String verificationDateTime = null;
  private String validFrom = null;
  private String validTo = null;
  private String keyUsage = null;
  private String revocationDate = null;
  private String invalidSince = null;
  private String holdDate = null;
  private String crlExpirationDate = null;
  private String caCertExpirationDate = null;
  private String caCertRevocationDate = null;
  private String expiredCertsOnCRL = null;
  private String subject = null;
  private String crlDate = null;
  private String issuerAltName = null;
  private String keyUsageString = null;
  private String uid = null;

  /**
   * Risultato della verifica.
   **/
  

  @JsonProperty("verificationResult") 
 
  public String getVerificationResult() {
    return verificationResult;
  }
  public void setVerificationResult(String verificationResult) {
    this.verificationResult = verificationResult;
  }

  /**
   * Eventuale codice di errore.
   **/
  

  @JsonProperty("errorCode") 
 
  public Integer getErrorCode() {
    return errorCode;
  }
  public void setErrorCode(Integer errorCode) {
    this.errorCode = errorCode;
  }

  /**
   * Eventuale desrizione dell&#39;errore
   **/
  

  @JsonProperty("errorDescription") 
 
  public String getErrorDescription() {
    return errorDescription;
  }
  public void setErrorDescription(String errorDescription) {
    this.errorDescription = errorDescription;
  }

  /**
   * Certification authority.
   **/
  

  @JsonProperty("certificationAuthority") 
 
  public String getCertificationAuthority() {
    return certificationAuthority;
  }
  public void setCertificationAuthority(String certificationAuthority) {
    this.certificationAuthority = certificationAuthority;
  }

  /**
   * Numero seriale.
   **/
  

  @JsonProperty("serialNumber") 
 
  public String getSerialNumber() {
    return serialNumber;
  }
  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  /**
   * Data e ora della verifica.
   **/
  

  @JsonProperty("verificationDateTime") 
 
  public String getVerificationDateTime() {
    return verificationDateTime;
  }
  public void setVerificationDateTime(String verificationDateTime) {
    this.verificationDateTime = verificationDateTime;
  }

  /**
   * Data di inizio validità del certificato.
   **/
  

  @JsonProperty("validFrom") 
 
  public String getValidFrom() {
    return validFrom;
  }
  public void setValidFrom(String validFrom) {
    this.validFrom = validFrom;
  }

  /**
   * Data di fine validità del certificato.
   **/
  

  @JsonProperty("validTo") 
 
  public String getValidTo() {
    return validTo;
  }
  public void setValidTo(String validTo) {
    this.validTo = validTo;
  }

  /**
   * Rappresenta la chiave.
   **/
  

  @JsonProperty("keyUsage") 
 
  public String getKeyUsage() {
    return keyUsage;
  }
  public void setKeyUsage(String keyUsage) {
    this.keyUsage = keyUsage;
  }

  /**
   * Eventuale data di revoca.
   **/
  

  @JsonProperty("revocationDate") 
 
  public String getRevocationDate() {
    return revocationDate;
  }
  public void setRevocationDate(String revocationDate) {
    this.revocationDate = revocationDate;
  }

  /**
   * Eventuale data di non validità.
   **/
  

  @JsonProperty("invalidSince") 
 
  public String getInvalidSince() {
    return invalidSince;
  }
  public void setInvalidSince(String invalidSince) {
    this.invalidSince = invalidSince;
  }

  /**
   * Eventuale data di trattenuta.
   **/
  

  @JsonProperty("holdDate") 
 
  public String getHoldDate() {
    return holdDate;
  }
  public void setHoldDate(String holdDate) {
    this.holdDate = holdDate;
  }

  /**
   * Data di scadenza della CRL.
   **/
  

  @JsonProperty("crlExpirationDate") 
 
  public String getCrlExpirationDate() {
    return crlExpirationDate;
  }
  public void setCrlExpirationDate(String crlExpirationDate) {
    this.crlExpirationDate = crlExpirationDate;
  }

  /**
   * Data di scadenza dell&#39;autorità certificatrice.
   **/
  

  @JsonProperty("caCertExpirationDate") 
 
  public String getCaCertExpirationDate() {
    return caCertExpirationDate;
  }
  public void setCaCertExpirationDate(String caCertExpirationDate) {
    this.caCertExpirationDate = caCertExpirationDate;
  }

  /**
   * Data di revoca dell&#39;autorità certificatrice.
   **/
  

  @JsonProperty("caCertRevocationDate") 
 
  public String getCaCertRevocationDate() {
    return caCertRevocationDate;
  }
  public void setCaCertRevocationDate(String caCertRevocationDate) {
    this.caCertRevocationDate = caCertRevocationDate;
  }

  /**
   * Certificati scaduti.
   **/
  

  @JsonProperty("expiredCertsOnCRL") 
 
  public String getExpiredCertsOnCRL() {
    return expiredCertsOnCRL;
  }
  public void setExpiredCertsOnCRL(String expiredCertsOnCRL) {
    this.expiredCertsOnCRL = expiredCertsOnCRL;
  }

  /**
   * Rappresenta il soggetto.
   **/
  

  @JsonProperty("subject") 
 
  public String getSubject() {
    return subject;
  }
  public void setSubject(String subject) {
    this.subject = subject;
  }

  /**
   * Data della CRL.
   **/
  

  @JsonProperty("crlDate") 
 
  public String getCrlDate() {
    return crlDate;
  }
  public void setCrlDate(String crlDate) {
    this.crlDate = crlDate;
  }

  /**
   * Nome alternativo dell&#39;emittente.
   **/
  

  @JsonProperty("issuerAltName") 
 
  public String getIssuerAltName() {
    return issuerAltName;
  }
  public void setIssuerAltName(String issuerAltName) {
    this.issuerAltName = issuerAltName;
  }

  /**
   * Rappresenta la chiave
   **/
  

  @JsonProperty("keyUsageString") 
 
  public String getKeyUsageString() {
    return keyUsageString;
  }
  public void setKeyUsageString(String keyUsageString) {
    this.keyUsageString = keyUsageString;
  }

  /**
   * Evenutale UUID del nodo del file.
   **/
  

  @JsonProperty("uid") 
 
  public String getUid() {
    return uid;
  }
  public void setUid(String uid) {
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
    VerifyCertificateReport verifyCertificateReport = (VerifyCertificateReport) o;
    return Objects.equals(verificationResult, verifyCertificateReport.verificationResult) &&
        Objects.equals(errorCode, verifyCertificateReport.errorCode) &&
        Objects.equals(errorDescription, verifyCertificateReport.errorDescription) &&
        Objects.equals(certificationAuthority, verifyCertificateReport.certificationAuthority) &&
        Objects.equals(serialNumber, verifyCertificateReport.serialNumber) &&
        Objects.equals(verificationDateTime, verifyCertificateReport.verificationDateTime) &&
        Objects.equals(validFrom, verifyCertificateReport.validFrom) &&
        Objects.equals(validTo, verifyCertificateReport.validTo) &&
        Objects.equals(keyUsage, verifyCertificateReport.keyUsage) &&
        Objects.equals(revocationDate, verifyCertificateReport.revocationDate) &&
        Objects.equals(invalidSince, verifyCertificateReport.invalidSince) &&
        Objects.equals(holdDate, verifyCertificateReport.holdDate) &&
        Objects.equals(crlExpirationDate, verifyCertificateReport.crlExpirationDate) &&
        Objects.equals(caCertExpirationDate, verifyCertificateReport.caCertExpirationDate) &&
        Objects.equals(caCertRevocationDate, verifyCertificateReport.caCertRevocationDate) &&
        Objects.equals(expiredCertsOnCRL, verifyCertificateReport.expiredCertsOnCRL) &&
        Objects.equals(subject, verifyCertificateReport.subject) &&
        Objects.equals(crlDate, verifyCertificateReport.crlDate) &&
        Objects.equals(issuerAltName, verifyCertificateReport.issuerAltName) &&
        Objects.equals(keyUsageString, verifyCertificateReport.keyUsageString) &&
        Objects.equals(uid, verifyCertificateReport.uid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(verificationResult, errorCode, errorDescription, certificationAuthority, serialNumber, verificationDateTime, validFrom, validTo, keyUsage, revocationDate, invalidSince, holdDate, crlExpirationDate, caCertExpirationDate, caCertRevocationDate, expiredCertsOnCRL, subject, crlDate, issuerAltName, keyUsageString, uid);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VerifyCertificateReport {\n");
    
    sb.append("    verificationResult: ").append(toIndentedString(verificationResult)).append("\n");
    sb.append("    errorCode: ").append(toIndentedString(errorCode)).append("\n");
    sb.append("    errorDescription: ").append(toIndentedString(errorDescription)).append("\n");
    sb.append("    certificationAuthority: ").append(toIndentedString(certificationAuthority)).append("\n");
    sb.append("    serialNumber: ").append(toIndentedString(serialNumber)).append("\n");
    sb.append("    verificationDateTime: ").append(toIndentedString(verificationDateTime)).append("\n");
    sb.append("    validFrom: ").append(toIndentedString(validFrom)).append("\n");
    sb.append("    validTo: ").append(toIndentedString(validTo)).append("\n");
    sb.append("    keyUsage: ").append(toIndentedString(keyUsage)).append("\n");
    sb.append("    revocationDate: ").append(toIndentedString(revocationDate)).append("\n");
    sb.append("    invalidSince: ").append(toIndentedString(invalidSince)).append("\n");
    sb.append("    holdDate: ").append(toIndentedString(holdDate)).append("\n");
    sb.append("    crlExpirationDate: ").append(toIndentedString(crlExpirationDate)).append("\n");
    sb.append("    caCertExpirationDate: ").append(toIndentedString(caCertExpirationDate)).append("\n");
    sb.append("    caCertRevocationDate: ").append(toIndentedString(caCertRevocationDate)).append("\n");
    sb.append("    expiredCertsOnCRL: ").append(toIndentedString(expiredCertsOnCRL)).append("\n");
    sb.append("    subject: ").append(toIndentedString(subject)).append("\n");
    sb.append("    crlDate: ").append(toIndentedString(crlDate)).append("\n");
    sb.append("    issuerAltName: ").append(toIndentedString(issuerAltName)).append("\n");
    sb.append("    keyUsageString: ").append(toIndentedString(keyUsageString)).append("\n");
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

