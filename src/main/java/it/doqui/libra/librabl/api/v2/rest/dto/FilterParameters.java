package it.doqui.libra.librabl.api.v2.rest.dto;

import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.List;

@Getter
@Setter
@ToString
public class FilterParameters {
    @Parameter(
        description = "List of comma separated property names to filter in"
    )
    @QueryParam("properties")
    private List<String> filterPropertyNames;

    @Parameter(
        description = "List of comma separated options to alter the result layout",
        schema = @Schema(
            type = SchemaType.ARRAY,
            implementation = String.class,
            enumeration = {
                "DEFAULT", "SYS_PROPERTIES", "PARENT_ASSOCIATIONS", "PARENT_HARD_ASSOCIATIONS", "SG", "TX",
                "NO_NULL_PROPERTIES", "NO_PROPERTIES", "VARRAY", "NO_PROPERTIES", "CHECK_ARCHIVE", "ACL", "PATHS"
            }
        )
    )
    @QueryParam("options")
    private List<String> options;

    @Parameter(
        description = "Specify the locale to use for localizable multi-languages properties",
        example = "it_IT"
    )
    @QueryParam("locale")
    private String locale;
}
