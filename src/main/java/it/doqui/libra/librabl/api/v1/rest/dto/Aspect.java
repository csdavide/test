package it.doqui.libra.librabl.api.v1.rest.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Getter
@Setter
@ToString
public class Aspect   {
  // verrà utilizzata la seguente strategia serializzazione degli attributi: [explicit-as-modeled]

  /**
   * Rappresenta il prefixed name.
   **/
  private String prefixedName = null;


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Aspect aspect = (Aspect) o;
    return Objects.equals(prefixedName, aspect.prefixedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefixedName);
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
