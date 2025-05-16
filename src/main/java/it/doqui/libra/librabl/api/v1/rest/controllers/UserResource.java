package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.TenantsBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.api.v1.rest.dto.User;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tenants/{tenantName}/users")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class UserResource extends AbstractBridgeResource {

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response listAllUserNames(@PathParam("tenantName") String tenant, @QueryParam("filter") String filter) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(TenantsBusinessInterface.class).listAllUserNames(filter)).build());
    }


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response createUser(@PathParam("tenantName") String tenant, User user) {
        return call(tenant, () -> Response.ok(new Data(dispatcher.getProxy(TenantsBusinessInterface.class).createUser(user))).build());
    }

    @DELETE
    @Path("/{userName}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public Response deleteUser(@PathParam("tenantName") String tenant, @PathParam("userName") String userName) {
        return call(tenant, () -> {
            dispatcher.getProxy(TenantsBusinessInterface.class).deleteUser(userName);
            return Response.ok().build();
        });
    }

    @PUT
    @Path("/{userName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response updateUser(
        @PathParam("tenantName") String tenant,
        @PathParam("userName") String userName,
        @QueryParam("updatePassword") Boolean updatePassword,
        User user) {
        return call(tenant, () -> {
            if (!(userName.equals(user.getUsername()) || user.getUsername().startsWith(userName))) {
                throw new BadRequestException("UserName in path doesn't match userName in User object");
            }

            if (updatePassword == null) {
                throw new BadRequestException("updatePassword parameter is null");
            }

            dispatcher.getProxy(TenantsBusinessInterface.class).updateUser(user, updatePassword);
            return Response.ok().build();
        });
    }
}
