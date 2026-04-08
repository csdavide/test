package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.application.model.events.SendEventRequest;
import it.doqui.libra.librabl.application.ports.out.EventRepository;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/management/events")
@Slf4j
@RolesAllowed(UserContext.ROLE_SYSADMIN)
public class EventResource extends AbstractResource {

    @Inject
    EventRepository eventRepository;

    @POST
    @Operation(operationId = "sendEvent", summary = "Submit a management event")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Event sent"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response sendEvent(SendEventRequest eventRequest) {
        return call(() -> {
            eventRepository.sendEvent(eventRequest);
            return Response.accepted().build();
        });
    }
}
