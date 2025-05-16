package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.TenantsBusinessInterface;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tenants/{tenantName}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class TenantResource extends AbstractBridgeResource {

    @GET
    @Path("/models")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getAllCustomModels(@PathParam("tenantName") String tenant) {
        return call(tenant, () -> Response.ok(
            dispatcher.getProxy(TenantsBusinessInterface.class)
                .getAllCustomModels()
            ).build()
        );
    }

    @GET
    @Path("/models/definition")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getModelDefinition(
        @PathParam("tenantName") String tenant,
        @QueryParam("prefixedName") String prefixedName) {
        return call(tenant, () -> Response.ok(
            dispatcher.getProxy(TenantsBusinessInterface.class)
                .getModelDefinition(prefixedName)
        ).build());
    }
}
