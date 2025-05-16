package it.doqui.libra.librabl.api.v1.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LuceneSearchResponse {
  // verra' utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled]

  private Integer totalResults = null;
  private Integer pageSize = null;
  private Integer pageIndex = null;
  private List<Node> nodes = new ArrayList<Node>();

  /**
   * Numero totale di risultati della ricerca.
   **/


  @JsonProperty("totalResults")

  public Integer getTotalResults() {
    return totalResults;
  }
  public void setTotalResults(Integer totalResults) {
    this.totalResults = totalResults;
  }

  /**
   * Dimensione della pagina della ricerca.
   **/


  @JsonProperty("pageSize")

  public Integer getPageSize() {
    return pageSize;
  }
  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * Indice della pagina della ricerca.
   **/


  @JsonProperty("pageIndex")

  public Integer getPageIndex() {
    return pageIndex;
  }
  public void setPageIndex(Integer pageIndex) {
    this.pageIndex = pageIndex;
  }

  /**
   * Elenco dei nodi risultato della ricerca.
   **/


  @JsonProperty("nodes")

  public List<Node> getNodes() {
    return nodes;
  }
  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LuceneSearchResponse luceneSearchResponse = (LuceneSearchResponse) o;
    return Objects.equals(totalResults, luceneSearchResponse.totalResults) &&
        Objects.equals(pageSize, luceneSearchResponse.pageSize) &&
        Objects.equals(pageIndex, luceneSearchResponse.pageIndex) &&
        Objects.equals(nodes, luceneSearchResponse.nodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(totalResults, pageSize, pageIndex, nodes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class LuceneSearchResponse {\n");

    sb.append("    totalResults: ").append(toIndentedString(totalResults)).append("\n");
    sb.append("    pageSize: ").append(toIndentedString(pageSize)).append("\n");
    sb.append("    pageIndex: ").append(toIndentedString(pageIndex)).append("\n");
    sb.append("    nodes: ").append(toIndentedString(nodes)).append("\n");
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

