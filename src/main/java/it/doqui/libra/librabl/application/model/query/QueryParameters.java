package it.doqui.libra.librabl.application.model.query;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.List;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryParameters {
    @Parameter(description = "List of comma separated UUIDs")
    @QueryParam("uuid")
    @JsonProperty("uuid")
    @JsonAlias("uuids")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> uuids;

    @Parameter(description = "Encoded lucene query")
    @QueryParam("q")
    private String q;

    @Parameter(description = "Query encoding type", schema = @Schema(implementation = String.class, enumeration = {"NONE", "BASE64"}))
    @QueryParam("encoding")
    @DefaultValue("BASE64")
    private EncodingType encoding;

    @Parameter(description = "Path to search used as an alternative simple query")
    @QueryParam("path")
    private String path;

    @Parameter(description = "Route to search used as an alternative simple query")
    @QueryParam("route")
    private String route;

    public enum EncodingType {
        NONE, BASE64
    }
}
