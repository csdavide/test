package it.doqui.index.ecmengine.mtom.dto;

import jakarta.xml.bind.annotation.XmlType;

@XmlType
public class VerifyCertificateReport extends MtomEngineDto {
    private static final long serialVersionUID = 9122988422000796830L;
    
    private String verificationResult;
    private int errorCode;
    private String errorDescription;
    private String certificationAuthority;
    private String serialNumber;
    private String verificationDateTime;
    private String validFrom;
    private String validTo;
    private String keyUsage;
    private String revocationDate;
    private String invalidSince;
    private String holdDate;
    private String crlExpirationDate;
    private String caCertExpirationDate;
    private String caCertRevocationDate;
    private String expiredCertsOnCRL;
    private String subject;
    private String OCSPExpired;
    private String OCSPNextUpdate;
    private String OCSPProducedAt;
    private String OCSPThisUpdate;
    private String crlDate;
    private String issuerAltName;
    private String keyUsageString;
    private String uid;

    /**
     * Restituisce il risultato della verifica.
     */
    public String getVerificationResult() {
	return verificationResult;
    }

    /**
     * Imposta il risultato della verifica.
     */
    public void setVerificationResult(String verificationResult) {
	this.verificationResult = verificationResult;
    }

    /**
     * Restituisce l'eventuale codice di errore.
     */
    public int getErrorCode() {
	return errorCode;
    }

    /**
     * Imposta l'eventuale codice di errore.
     */
    public void setErrorCode(int errorCode) {
	this.errorCode = errorCode;
    }

    /**
     * Restituisce l'eventuale desrizione dell'errore.
     */
    public String getErrorDescription() {
	return errorDescription;
    }

    /**
     * Imposta l'eventuale desrizione dell'errore.
     */
    public void setErrorDescription(String errorDescription) {
	this.errorDescription = errorDescription;
    }

    /**
     * Restituisce la certification authority.
     */
    public String getCertificationAuthority() {
	return certificationAuthority;
    }

    /**
     * Imposta la certification authority.
     */
    public void setCertificationAuthority(String certificationAuthority) {
	this.certificationAuthority = certificationAuthority;
    }

    /**
     * Restituisce il numero seriale.
     */
    public String getSerialNumber() {
	return serialNumber;
    }

    /**
     * Imposta il numero seriale.
     */
    public void setSerialNumber(String serialNumber) {
	this.serialNumber = serialNumber;
    }

    /**
     * Restituisce la data e ora della verifica.
     */
    public String getVerificationDateTime() {
	return verificationDateTime;
    }

    /**
     * Imposta la data e ora della verifica.
     */
    public void setVerificationDateTime(String verificationDateTime) {
	this.verificationDateTime = verificationDateTime;
    }

    /**
     * Restituisce la data di inizio validit&agrave; del certificato.
     */
    public String getValidFrom() {
	return validFrom;
    }

    /**
     * Imposta la data di inizio validit&agrave; del certificato.
     */
    public void setValidFrom(String validFrom) {
	this.validFrom = validFrom;
    }

    /**
     * Restituisce la data di fine validit&agrave; del certificato.
     */
    public String getValidTo() {
	return validTo;
    }

    /**
     * Imposta la data di fine validit&agrave; del certificato.
     */
    public void setValidTo(String validTo) {
	this.validTo = validTo;
    }

    /**
     * Restituisce la chiave.
     */
    public String getKeyUsage() {
	return keyUsage;
    }

    /**
     * Imposta la chiave.
     */
    public void setKeyUsage(String keyUsage) {
	this.keyUsage = keyUsage;
    }

    /**
     * Restituisce l'eventuale data di revoca.
     */
    public String getRevocationDate() {
	return revocationDate;
    }

    /**
     * Imposta l'eventuale data di revoca.
     */
    public void setRevocationDate(String revocationDate) {
	this.revocationDate = revocationDate;
    }

    /**
     * Restituisce l'eventuale data di non validit&agrave;.
     */
    public String getInvalidSince() {
	return invalidSince;
    }

    /**
     * Imposta l'eventuale data di non validit&agrave;.
     */
    public void setInvalidSince(String invalidSince) {
	this.invalidSince = invalidSince;
    }

    /**
     * Restituisce l'eventuale data di trattenuta.
     */
    public String getHoldDate() {
	return holdDate;
    }

    /**
     * Imposta l'eventuale data di trattenuta.
     */
    public void setHoldDate(String holdDate) {
	this.holdDate = holdDate;
    }

    /**
     * Restituisce la data di scadenza della CRL.
     */
    public String getCrlExpirationDate() {
	return crlExpirationDate;
    }

    /**
     * Imposta la data di scadenza della CRL.
     */
    public void setCrlExpirationDate(String crlExpirationDate) {
	this.crlExpirationDate = crlExpirationDate;
    }

    /**
     * Restituisce la data di scadenza dell'autorit&agrave; certificatrice.
     */
    public String getCaCertExpirationDate() {
	return caCertExpirationDate;
    }

    /**
     * Imposta la data di scadenza dell'autorit&agrave; certificatrice.
     */
    public void setCaCertExpirationDate(String caCertExpirationDate) {
	this.caCertExpirationDate = caCertExpirationDate;
    }

    /**
     * Restituisce la data di revoca dell'autorit&agrave; certificatrice.
     */
    public String getCaCertRevocationDate() {
	return caCertRevocationDate;
    }

    /**
     * Imposta la data di revoca dell'autorit&agrave; certificatrice.
     */
    public void setCaCertRevocationDate(String caCertRevocationDate) {
	this.caCertRevocationDate = caCertRevocationDate;
    }

    /**
     * Restituisce i certificati scaduti.
     */
    public String getExpiredCertsOnCRL() {
	return expiredCertsOnCRL;
    }

    /**
     * Imposta i certificati scaduti.
     */
    public void setExpiredCertsOnCRL(String expiredCertsOnCRL) {
	this.expiredCertsOnCRL = expiredCertsOnCRL;
    }

    /**
     * Restituisce il soggetto.
     */
    public String getSubject() {
	return subject;
    }

    /**
     * Imposta il soggetto.
     */
    public void setSubject(String subject) {
	this.subject = subject;
    }

    @Deprecated
    public String getOCSPExpired() {
	return OCSPExpired;
    }

    @Deprecated
    public void setOCSPExpired(String oCSPExpired) {
	OCSPExpired = oCSPExpired;
    }

    @Deprecated
    public String getOCSPNextUpdate() {
	return OCSPNextUpdate;
    }

    @Deprecated
    public void setOCSPNextUpdate(String oCSPNextUpdate) {
	OCSPNextUpdate = oCSPNextUpdate;
    }

    @Deprecated
    public String getOCSPProducedAt() {
	return OCSPProducedAt;
    }

    @Deprecated
    public void setOCSPProducedAt(String oCSPProducedAt) {
	OCSPProducedAt = oCSPProducedAt;
    }

    @Deprecated
    public String getOCSPThisUpdate() {
	return OCSPThisUpdate;
    }

    @Deprecated
    public void setOCSPThisUpdate(String oCSPThisUpdate) {
	OCSPThisUpdate = oCSPThisUpdate;
    }

    /**
     * Restituisce la data della CRL.
     */
    public String getCrlDate() {
	return crlDate;
    }

    /**
     * Imposta la data della CRL.
     */
    public void setCrlDate(String crlDate) {
	this.crlDate = crlDate;
    }

    /**
     * Restituisce il nome alternativo dell'emittente.
     */
    public String getIssuerAltName() {
	return issuerAltName;
    }

    /**
     * Imposta il nome alternativo dell'emittente.
     */
    public void setIssuerAltName(String issuerAltName) {
	this.issuerAltName = issuerAltName;
    }

    /**
     * Restituisce la chiave.
     */
    public String getKeyUsageString() {
	return keyUsageString;
    }

    /**
     * Imposta la chiave.
     */
    public void setKeyUsageString(String keyUsageString) {
	this.keyUsageString = keyUsageString;
    }

    /**
     * Restituisce l'evenutale UUID del nodo del file.
     */
    public String getUid() {
	return uid;
    }

    /**
     * Imposta l'evenutale UUID del nodo del file.
     */
    public void setUid(String uid) {
	this.uid = uid;
    }
}