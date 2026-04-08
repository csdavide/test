package it.doqui.libra.librabl.foundation;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Pageable {
    @QueryParam("page")
    @DefaultValue("0")
    @PositiveOrZero(message = "Page index must be a positive or zero number")
    private int page;

    @QueryParam("size")
    @DefaultValue("50")
    @Positive(message = "Page size must be a positive number")
    private int size;
}
