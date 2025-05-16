package it.doqui.libra.librabl.api.v2.rest.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.ingest.ImportService;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Path("/v2/datasets")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class DataSetResource extends AbstractResource {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ImportService importService;

    @POST
    @Path("/{dataset}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "importDataSet", summary = "Create a set of media nodes")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Imported nodes uuids are returned",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = String.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response importDataSet(
        @PathParam("dataset") String dataSetType,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @RequestBody byte[] payload) {
        return call(() -> {
            var _contentType = Optional.ofNullable(StringUtils.stripToNull(customContentType)).orElse(contentType);
            var typeRef = new TypeReference<HashMap<String,Object>>() {};
            final Map<String,Object> map;
            switch (_contentType) {
                case MediaType.APPLICATION_XML, MediaType.TEXT_XML: {
                    var mapper = new XmlMapper();
                    map = mapper.readValue(payload, typeRef);
                    break;
                }
                case MediaType.APPLICATION_JSON: {
                    map = objectMapper.readValue(payload, typeRef);
                    break;
                }
                case "application/yaml", "application/yml", "text/yaml", "text/yml", "application/x-yaml", "application/x-yml": {
                    var mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    map = objectMapper.readValue(payload, typeRef);
                    break;
                }
                default:
                    throw new BadRequestException("Unsupported content type: " + _contentType);
            }

            var result = importService.importDataSet(dataSetType, map);
            return Response.ok(result).build();
        });
    }

}
