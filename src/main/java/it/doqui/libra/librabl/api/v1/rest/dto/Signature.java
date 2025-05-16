package it.doqui.libra.librabl.api.v1.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Signature   {
  // verrà utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled]
  
  private List<Signature> signature = new ArrayList<>();
  private Integer annoFirma = null;
  private String ca = null;
  private byte[] cert = null;
  private String codiceFiscale = null;
  private String dataOra = null;
  private String dataOraVerifica = null;
  private String dipartimento = null;
  private String dnQualifier = null;
  private Integer errorCode = null;
  private String fineValidita = null;
  private String firmatario = null;
  private Integer giornoFirma = null;
  private String givenname = null;
  private String inizioValidita = null;
  private Integer meseFirma = null;
  private Integer minutiFirma = null;
  private String nominativoFirmatario = null;
  private Integer numeroControfirme = null;
  private Integer oraFirma = null;
  private String organizzazione = null;
  private String paese = null;
  private Integer secondiFirma = null;
  private String serialNumber = null;
  private String surname = null;
  private Boolean timestamped = null;
  private Integer tipoCertificato = null;
  private Integer tipoFirma = null;

  /**
   * Elenco delle controfirme.
   **/
  

  @JsonProperty("signature") 
 
  public List<Signature> getSignature() {
    return signature;
  }
  public void setSignature(List<Signature> signature) {
    this.signature = signature;
  }

  /**
   * Anno della firma.
   **/
  

  @JsonProperty("annoFirma") 
 
  public Integer getAnnoFirma() {
    return annoFirma;
  }
  public void setAnnoFirma(Integer annoFirma) {
    this.annoFirma = annoFirma;
  }

  /**
   * Certification authority.
   **/
  

  @JsonProperty("ca") 
 
  public String getCa() {
    return ca;
  }
  public void setCa(String ca) {
    this.ca = ca;
  }

  /**
   * Certificato di firma codificato in base64.
   **/
  

  @JsonProperty("cert") 
 
  @Pattern(regexp="^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
  public byte[] getCert() {
    return cert;
  }
  public void setCert(byte[] cert) {
    this.cert = cert;
  }

  /**
   * Codice fiscale del firmatario.
   **/
  

  @JsonProperty("codiceFiscale") 
 
  public String getCodiceFiscale() {
    return codiceFiscale;
  }
  public void setCodiceFiscale(String codiceFiscale) {
    this.codiceFiscale = codiceFiscale;
  }

  /**
   * Data e l&#39;ora della firma.
   **/
  

  @JsonProperty("dataOra") 
 
  public String getDataOra() {
    return dataOra;
  }
  public void setDataOra(String dataOra) {
    this.dataOra = dataOra;
  }

  /**
   * Data e ora del rapporto di verifica nel formato gg/mm/aaaa hh:mm:ss.
   **/
  

  @JsonProperty("dataOraVerifica") 
 
  public String getDataOraVerifica() {
    return dataOraVerifica;
  }
  public void setDataOraVerifica(String dataOraVerifica) {
    this.dataOraVerifica = dataOraVerifica;
  }

  /**
   * Dipartimento.
   **/
  

  @JsonProperty("dipartimento") 
 
  public String getDipartimento() {
    return dipartimento;
  }
  public void setDipartimento(String dipartimento) {
    this.dipartimento = dipartimento;
  }

  /**
   * Dnqualifier del firmatario.
   **/
  

  @JsonProperty("dnQualifier") 
 
  public String getDnQualifier() {
    return dnQualifier;
  }
  public void setDnQualifier(String dnQualifier) {
    this.dnQualifier = dnQualifier;
  }

  /**
   * Eventuale codice di errore della verifica della firma.
   **/
  

  @JsonProperty("errorCode") 
 
  public Integer getErrorCode() {
    return errorCode;
  }
  public void setErrorCode(Integer errorCode) {
    this.errorCode = errorCode;
  }

  /**
   * Data di fine di validità della firma.
   **/
  

  @JsonProperty("fineValidita") 
 
  public String getFineValidita() {
    return fineValidita;
  }
  public void setFineValidita(String fineValidita) {
    this.fineValidita = fineValidita;
  }

  /**
   * Firmatario.
   **/
  

  @JsonProperty("firmatario") 
 
  public String getFirmatario() {
    return firmatario;
  }
  public void setFirmatario(String firmatario) {
    this.firmatario = firmatario;
  }

  /**
   * Giorno della firma.
   **/
  

  @JsonProperty("giornoFirma") 
 
  public Integer getGiornoFirma() {
    return giornoFirma;
  }
  public void setGiornoFirma(Integer giornoFirma) {
    this.giornoFirma = giornoFirma;
  }

  /**
   * Nome del firmatario.
   **/
  

  @JsonProperty("givenname") 
 
  public String getGivenname() {
    return givenname;
  }
  public void setGivenname(String givenname) {
    this.givenname = givenname;
  }

  /**
   * Data di inizio di validità della firma.
   **/
  

  @JsonProperty("inizioValidita") 
 
  public String getInizioValidita() {
    return inizioValidita;
  }
  public void setInizioValidita(String inizioValidita) {
    this.inizioValidita = inizioValidita;
  }

  /**
   * Mese della firma.
   **/
  

  @JsonProperty("meseFirma") 
 
  public Integer getMeseFirma() {
    return meseFirma;
  }
  public void setMeseFirma(Integer meseFirma) {
    this.meseFirma = meseFirma;
  }

  /**
   * Minuti della firma.
   **/
  

  @JsonProperty("minutiFirma") 
 
  public Integer getMinutiFirma() {
    return minutiFirma;
  }
  public void setMinutiFirma(Integer minutiFirma) {
    this.minutiFirma = minutiFirma;
  }

  /**
   * Nominativo del firmatario.
   **/
  

  @JsonProperty("nominativoFirmatario") 
 
  public String getNominativoFirmatario() {
    return nominativoFirmatario;
  }
  public void setNominativoFirmatario(String nominativoFirmatario) {
    this.nominativoFirmatario = nominativoFirmatario;
  }

  /**
   * Numero di controfirme del file.
   **/
  

  @JsonProperty("numeroControfirme") 
 
  public Integer getNumeroControfirme() {
    return numeroControfirme;
  }
  public void setNumeroControfirme(Integer numeroControfirme) {
    this.numeroControfirme = numeroControfirme;
  }

  /**
   * Ora della firma.
   **/
  

  @JsonProperty("oraFirma") 
 
  public Integer getOraFirma() {
    return oraFirma;
  }
  public void setOraFirma(Integer oraFirma) {
    this.oraFirma = oraFirma;
  }

  /**
   * Organizzazione del firmatario.
   **/
  

  @JsonProperty("organizzazione") 
 
  public String getOrganizzazione() {
    return organizzazione;
  }
  public void setOrganizzazione(String organizzazione) {
    this.organizzazione = organizzazione;
  }

  /**
   * Paese del firmatario.
   **/
  

  @JsonProperty("paese") 
 
  public String getPaese() {
    return paese;
  }
  public void setPaese(String paese) {
    this.paese = paese;
  }

  /**
   * Secondi della firma.
   **/
  

  @JsonProperty("secondiFirma") 
 
  public Integer getSecondiFirma() {
    return secondiFirma;
  }
  public void setSecondiFirma(Integer secondiFirma) {
    this.secondiFirma = secondiFirma;
  }

  /**
   * Serial number del firmatario.
   **/
  

  @JsonProperty("serialNumber") 
 
  public String getSerialNumber() {
    return serialNumber;
  }
  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  /**
   * Cognome del firmatario.
   **/
  

  @JsonProperty("surname") 
 
  public String getSurname() {
    return surname;
  }
  public void setSurname(String surname) {
    this.surname = surname;
  }

  /**
   * Imposta se la firma è fornita di una marcatura temporale.
   **/
  

  @JsonProperty("timestamped") 
 
  public Boolean isTimestamped() {
    return timestamped;
  }
  public void setTimestamped(Boolean timestamped) {
    this.timestamped = timestamped;
  }

  /**
   * Imposta il tipo del certificato:  1 in caso di firma  2 in caso di marca temporale. 
   **/
  

  @JsonProperty("tipoCertificato") 
 
  public Integer getTipoCertificato() {
    return tipoCertificato;
  }
  public void setTipoCertificato(Integer tipoCertificato) {
    this.tipoCertificato = tipoCertificato;
  }

  /**
   * Imposta il tipo della firma.
   **/
  

  @JsonProperty("tipoFirma") 
 
  public Integer getTipoFirma() {
    return tipoFirma;
  }
  public void setTipoFirma(Integer tipoFirma) {
    this.tipoFirma = tipoFirma;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Signature signature = (Signature) o;
    return Objects.equals(signature, signature.signature) &&
        Objects.equals(annoFirma, signature.annoFirma) &&
        Objects.equals(ca, signature.ca) &&
        Objects.equals(cert, signature.cert) &&
        Objects.equals(codiceFiscale, signature.codiceFiscale) &&
        Objects.equals(dataOra, signature.dataOra) &&
        Objects.equals(dataOraVerifica, signature.dataOraVerifica) &&
        Objects.equals(dipartimento, signature.dipartimento) &&
        Objects.equals(dnQualifier, signature.dnQualifier) &&
        Objects.equals(errorCode, signature.errorCode) &&
        Objects.equals(fineValidita, signature.fineValidita) &&
        Objects.equals(firmatario, signature.firmatario) &&
        Objects.equals(giornoFirma, signature.giornoFirma) &&
        Objects.equals(givenname, signature.givenname) &&
        Objects.equals(inizioValidita, signature.inizioValidita) &&
        Objects.equals(meseFirma, signature.meseFirma) &&
        Objects.equals(minutiFirma, signature.minutiFirma) &&
        Objects.equals(nominativoFirmatario, signature.nominativoFirmatario) &&
        Objects.equals(numeroControfirme, signature.numeroControfirme) &&
        Objects.equals(oraFirma, signature.oraFirma) &&
        Objects.equals(organizzazione, signature.organizzazione) &&
        Objects.equals(paese, signature.paese) &&
        Objects.equals(secondiFirma, signature.secondiFirma) &&
        Objects.equals(serialNumber, signature.serialNumber) &&
        Objects.equals(surname, signature.surname) &&
        Objects.equals(timestamped, signature.timestamped) &&
        Objects.equals(tipoCertificato, signature.tipoCertificato) &&
        Objects.equals(tipoFirma, signature.tipoFirma);
  }

  @Override
  public int hashCode() {
    return Objects.hash(signature, annoFirma, ca, cert, codiceFiscale, dataOra, dataOraVerifica, dipartimento, dnQualifier, errorCode, fineValidita, firmatario, giornoFirma, givenname, inizioValidita, meseFirma, minutiFirma, nominativoFirmatario, numeroControfirme, oraFirma, organizzazione, paese, secondiFirma, serialNumber, surname, timestamped, tipoCertificato, tipoFirma);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Signature {\n");
    
    sb.append("    signature: ").append(toIndentedString(signature)).append("\n");
    sb.append("    annoFirma: ").append(toIndentedString(annoFirma)).append("\n");
    sb.append("    ca: ").append(toIndentedString(ca)).append("\n");
    sb.append("    cert: ").append(toIndentedString(cert)).append("\n");
    sb.append("    codiceFiscale: ").append(toIndentedString(codiceFiscale)).append("\n");
    sb.append("    dataOra: ").append(toIndentedString(dataOra)).append("\n");
    sb.append("    dataOraVerifica: ").append(toIndentedString(dataOraVerifica)).append("\n");
    sb.append("    dipartimento: ").append(toIndentedString(dipartimento)).append("\n");
    sb.append("    dnQualifier: ").append(toIndentedString(dnQualifier)).append("\n");
    sb.append("    errorCode: ").append(toIndentedString(errorCode)).append("\n");
    sb.append("    fineValidita: ").append(toIndentedString(fineValidita)).append("\n");
    sb.append("    firmatario: ").append(toIndentedString(firmatario)).append("\n");
    sb.append("    giornoFirma: ").append(toIndentedString(giornoFirma)).append("\n");
    sb.append("    givenname: ").append(toIndentedString(givenname)).append("\n");
    sb.append("    inizioValidita: ").append(toIndentedString(inizioValidita)).append("\n");
    sb.append("    meseFirma: ").append(toIndentedString(meseFirma)).append("\n");
    sb.append("    minutiFirma: ").append(toIndentedString(minutiFirma)).append("\n");
    sb.append("    nominativoFirmatario: ").append(toIndentedString(nominativoFirmatario)).append("\n");
    sb.append("    numeroControfirme: ").append(toIndentedString(numeroControfirme)).append("\n");
    sb.append("    oraFirma: ").append(toIndentedString(oraFirma)).append("\n");
    sb.append("    organizzazione: ").append(toIndentedString(organizzazione)).append("\n");
    sb.append("    paese: ").append(toIndentedString(paese)).append("\n");
    sb.append("    secondiFirma: ").append(toIndentedString(secondiFirma)).append("\n");
    sb.append("    serialNumber: ").append(toIndentedString(serialNumber)).append("\n");
    sb.append("    surname: ").append(toIndentedString(surname)).append("\n");
    sb.append("    timestamped: ").append(toIndentedString(timestamped)).append("\n");
    sb.append("    tipoCertificato: ").append(toIndentedString(tipoCertificato)).append("\n");
    sb.append("    tipoFirma: ").append(toIndentedString(tipoFirma)).append("\n");
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

