package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.VolumeCalculationUseCase;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.management.VolumeInfo;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Collection;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/management/volumes/calculations")
@Slf4j
@RolesAllowed(UserContext.ROLE_SYSADMIN)
public class VolumeResource extends AbstractResource {

    @Inject
    VolumeCalculationUseCase volumeCalculationUseCase;

    @POST
    @Operation(operationId = "submitVolumesCalculation", summary = "Submit a volumes calculation operation")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "202",
                    description = "Async operation id is returned",
                    content = @Content(schema = @Schema(implementation = AsyncOperation.class))
            ),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response submitVolumesCalculation() {
        return call(() -> Response.status(202).entity(volumeCalculationUseCase.submitVolumesCalculation()).build());
    }

    @GET
    @Path("/{id}")
    @Operation(operationId = "getCalculatedVolumes", summary = "Get calculated volumes")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = "Volume Statistics are returned",
                    content = @Content(schema = @Schema(type = SchemaType.OBJECT, implementation = CalculatedVolumesOperation.class))
            ),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getCalculatedVolumes(@PathParam("id") String taskId) {
        return call(() -> Response.ok(volumeCalculationUseCase.getCalculatedVolumes(taskId)).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "deleteCalculatedVolumes", summary = "Delete calculated volumes")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Volume Statistics are deleted"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteCalculatedVolumes(@PathParam("id") String taskId) {
        return call(() -> {
            volumeCalculationUseCase.deleteCalculatedVolumes(taskId);
            return Response.noContent().build();
        });
    }

    public interface CalculatedVolumesOperation extends AsyncOperation<Collection<VolumeInfo>> {
    }
}
