package it.doqui.libra.librabl.api.v2.rest.dto;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.List;

@Getter
@Setter
@ToString
public class QueryParameters {
    @Parameter(description = "List of comma separated UUIDs")
    @QueryParam("uuid")
    private List<String> uuids;

    @Parameter(description = "Encoded lucene query")
    @QueryParam("q")
    private String q;

    @Parameter(description = "Query encoding type")
    @QueryParam("encoding")
    @DefaultValue("BASE64")
    private EncodingType encoding;

    @Parameter(description = "Path to search used as an alternative simple query")
    @QueryParam("path")
    private String path;

    public enum EncodingType {
        NONE, BASE64
    }
}
