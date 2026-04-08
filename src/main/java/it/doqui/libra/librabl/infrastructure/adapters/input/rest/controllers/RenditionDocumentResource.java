package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.ListOfRenditionNodes;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.RenditionUseCase;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.graph.ContentRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes/{xml}/renditions")
@RolesAllowed(UserContext.ROLE_USER)
public class RenditionDocumentResource extends AbstractResource {

    @Inject
    RenditionUseCase renditionService;

    @GET
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @Operation(operationId = "getAllRenditions", summary = "Retrieves a list of renditions of the given renditionable node and all the transformer nodes associated")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "List of renditions is returned", content = @Content(schema = @Schema(implementation = ListOfRenditionNodes.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or rendition nodes not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getAllRenditions(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String uuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("contentPropertyName") String contentPropertyName,
        @Parameter(description = "It specifies the kind of renditions")
        @QueryParam("generated") Boolean generated
    ) {
        return call(() -> Response.ok(
            renditionService.findRenditionNodes(new ContentRequest(uuid, contentPropertyName), null, generated, false)
        ).build());
    }

}
