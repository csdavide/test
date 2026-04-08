package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/jobs")
@Slf4j
@RolesAllowed({UserContext.ROLE_USER, UserContext.ROLE_ADMIN, UserContext.ROLE_SYSADMIN})
public class JobResource extends AbstractResource {

    @Inject
    JobUseCase jobService;

    @GET
    @Path("/{id}")
    @Operation(operationId = "getJob", summary = "Get job status and result")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Job status and result is returned", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @APIResponse(responseCode = "404", description = "Job not found"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getJob(@PathParam("id") String jobId, @QueryParam("tenant") String tenant) {
        return call(() -> Response.ok(jobService.getJob(jobId, tenant)).build());
    }

    @POST
    @Operation(operationId = "executeJob", summary = "Execute or submit a job")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Job status and result is returned", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response executeJob(JobRequest request) {
        return call(() -> {
            var response = jobService.executeJob(request);
            return Response.ok(response).build();
        });
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "cancelJob", summary = "Cancel a job status if it support interruption")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Job status and result is returned", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @APIResponse(responseCode = "404", description = "Job not found"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response cancelJob(@PathParam("id") String jobId, @QueryParam("tenant") String tenant) {
        return call(() -> Response.ok(jobService.cancelJob(jobId, tenant)).build());
    }
}
