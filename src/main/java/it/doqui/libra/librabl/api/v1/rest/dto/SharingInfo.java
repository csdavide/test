package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SharingInfo   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String sharedLink = null;
  private String contentPropertyPrefixedName = null;
  private String source = null;
  private String fromDate = null;
  private String toDate = null;
  private String resultContentDisposition = null;
  private String resultPropertyPrefixedName = null;

  /**
   * Url della condivisione.
   **/
  

  @JsonProperty("sharedLink") 
 
  public String getSharedLink() {
    return sharedLink;
  }
  public void setSharedLink(String sharedLink) {
    this.sharedLink = sharedLink;
  }

  /**
   * Prefixed name della proprietà che rappresenta il contenuto binario del nodo.
   **/
  

  @JsonProperty("contentPropertyPrefixedName") 
 
  public String getContentPropertyPrefixedName() {
    return contentPropertyPrefixedName;
  }
  public void setContentPropertyPrefixedName(String contentPropertyPrefixedName) {
    this.contentPropertyPrefixedName = contentPropertyPrefixedName;
  }

  /**
   * Origine del link Valori :    &gt;      intranet1 esposizione interna (http)      intranet2 esposizione interna (https)      internet eposizione INTERNET (https). 
   **/
  

  @JsonProperty("source") 
 
  public String getSource() {
    return source;
  }
  public void setSource(String source) {
    this.source = source;
  }

  /**
   * Data di inizio validità del link.
   **/
  

  @JsonProperty("fromDate") 
 
  public String getFromDate() {
    return fromDate;
  }
  public void setFromDate(String fromDate) {
    this.fromDate = fromDate;
  }

  /**
   * Data di termine validita&#39; del link.
   **/
  

  @JsonProperty("toDate") 
 
  public String getToDate() {
    return toDate;
  }
  public void setToDate(String toDate) {
    this.toDate = toDate;
  }

  /**
   * Specifica il Content-Disposition in uscita (per uso client).   &gt;   * se valorizzato :     l&#39;eventuale marcatore #{filename}      verrà sostiuito con il valore della proprietà cm:name     se non diversamete indicato attraverso la proprietà     resultPropertyPrefixedName     esempio 1 : inline; filename&#x3D;\&quot;nomeFile.pdf\&quot;     esempio 2 : inline; filename&#x3D;\&quot;#{filename}\&quot;   * se non valorizzato :      verrà interpretato come attachment;     il filename sarà valorizzato con il valore della proprietà      cm:name     se non diversamete indicato attraverso la proprietà       resultPropertyPrefixedName 
   **/
  

  @JsonProperty("resultContentDisposition") 
 
  public String getResultContentDisposition() {
    return resultContentDisposition;
  }
  public void setResultContentDisposition(String resultContentDisposition) {
    this.resultContentDisposition = resultContentDisposition;
  }

  /**
   * Prefixed name della proprietà che contenente il nome del file.
   **/
  

  @JsonProperty("resultPropertyPrefixedName") 
 
  public String getResultPropertyPrefixedName() {
    return resultPropertyPrefixedName;
  }
  public void setResultPropertyPrefixedName(String resultPropertyPrefixedName) {
    this.resultPropertyPrefixedName = resultPropertyPrefixedName;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SharingInfo sharingInfo = (SharingInfo) o;
    return Objects.equals(sharedLink, sharingInfo.sharedLink) &&
        Objects.equals(contentPropertyPrefixedName, sharingInfo.contentPropertyPrefixedName) &&
        Objects.equals(source, sharingInfo.source) &&
        Objects.equals(fromDate, sharingInfo.fromDate) &&
        Objects.equals(toDate, sharingInfo.toDate) &&
        Objects.equals(resultContentDisposition, sharingInfo.resultContentDisposition) &&
        Objects.equals(resultPropertyPrefixedName, sharingInfo.resultPropertyPrefixedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sharedLink, contentPropertyPrefixedName, source, fromDate, toDate, resultContentDisposition, resultPropertyPrefixedName);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SharingInfo {\n");
    
    sb.append("    sharedLink: ").append(toIndentedString(sharedLink)).append("\n");
    sb.append("    contentPropertyPrefixedName: ").append(toIndentedString(contentPropertyPrefixedName)).append("\n");
    sb.append("    source: ").append(toIndentedString(source)).append("\n");
    sb.append("    fromDate: ").append(toIndentedString(fromDate)).append("\n");
    sb.append("    toDate: ").append(toIndentedString(toDate)).append("\n");
    sb.append("    resultContentDisposition: ").append(toIndentedString(resultContentDisposition)).append("\n");
    sb.append("    resultPropertyPrefixedName: ").append(toIndentedString(resultPropertyPrefixedName)).append("\n");
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

