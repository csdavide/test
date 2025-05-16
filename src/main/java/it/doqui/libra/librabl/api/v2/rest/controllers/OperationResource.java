package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/operations")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class OperationResource extends AbstractResource {

    @Inject
    AsyncOperationService asyncOperationService;

    @GET
    @Path("/{id}")
    @Operation(operationId = "getAsyncOperation", summary = "Get async operation status")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Operation status returned", content = @Content(schema = @Schema(implementation = FeedbackAsyncOperation.class))),
        @APIResponse(responseCode = "404", description = "Operation not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getAsyncOperation(@PathParam("id") String taskId) {
        return call(() -> asyncOperationService
            .getTask(taskId)
            .map(op -> Response.ok(op).build())
            .orElseThrow(() -> new NotFoundException(taskId)));
    }
}
