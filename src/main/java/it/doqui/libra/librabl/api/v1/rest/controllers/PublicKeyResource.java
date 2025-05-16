package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.TenantsBusinessInterface;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tenants/{tenantName}/nodes/publicKeys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class PublicKeyResource extends AbstractBridgeResource {

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getPublicKeys(@PathParam("tenantName") String tenant) {
        return call(tenant, () -> Response.ok(
            dispatcher.getProxy(TenantsBusinessInterface.class).getPublicKeys()
        ).build());
    }

    @POST
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response addPublicKey(@PathParam("tenantName") String tenant, @QueryParam("publicKey") String publicKey) {
        return call(tenant, () -> {
            dispatcher.getProxy(TenantsBusinessInterface.class).addPublicKey(publicKey);
            return Response.ok().build();
        });
    }

    @DELETE
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response removePublicKey(@PathParam("tenantName") String tenant, @QueryParam("publicKey") String publicKey) {
        return call(tenant, () -> {
            dispatcher.getProxy(TenantsBusinessInterface.class).removePublicKey(publicKey);
            return Response.ok().build();
        });
    }
}
