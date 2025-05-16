package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Mimetype   {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled] 
  
  private String fileExtension = null;
  private String mimetype = null;

  /**
   * Estensione del nome del file.
   **/
  

  @JsonProperty("fileExtension") 
 
  public String getFileExtension() {
    return fileExtension;
  }
  public void setFileExtension(String fileExtension) {
    this.fileExtension = fileExtension;
  }

  /**
   * MIME type del file.
   **/
  

  @JsonProperty("mimetype") 
 
  public String getMimetype() {
    return mimetype;
  }
  public void setMimetype(String mimetype) {
    this.mimetype = mimetype;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Mimetype mimetype = (Mimetype) o;
    return Objects.equals(fileExtension, mimetype.fileExtension) &&
        Objects.equals(mimetype, mimetype.mimetype);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileExtension, mimetype);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Mimetype {\n");
    
    sb.append("    fileExtension: ").append(toIndentedString(fileExtension)).append("\n");
    sb.append("    mimetype: ").append(toIndentedString(mimetype)).append("\n");
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

