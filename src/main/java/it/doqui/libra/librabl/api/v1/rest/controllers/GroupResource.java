package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.TenantsBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tenants/{tenantName}/groups")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class GroupResource extends AbstractBridgeResource {

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response listAllGroups(@PathParam("tenantName") String tenant, @QueryParam("filter") String filter) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(TenantsBusinessInterface.class).listAllGroups(filter)).build());
    }

    @POST
    @Path("/{groupName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response createGroupInRoot(@PathParam("tenantName") String tenant, @PathParam("groupName") String groupName) {
        return call(tenant, () -> Response.ok(new Data(dispatcher.getProxy(TenantsBusinessInterface.class).createGroup(groupName, null))).build());
    }

    @DELETE
    @Path("/{groupName}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public Response deleteGroup(@PathParam("tenantName") String tenant, @PathParam("groupName") String groupName) {
        return call(tenant, () -> {
            dispatcher.getProxy(TenantsBusinessInterface.class).deleteGroup(groupName);
           return Response.ok().build();
        });
    }

    @GET
    @Path("/{groupName}/users")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response listUsers(@PathParam("tenantName") String tenant, @PathParam("groupName") String groupName) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(TenantsBusinessInterface.class).listUsers(groupName)).build());
    }

    @POST
    @Path("/{groupName}/users/{userName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response addUserToGroup(
        @PathParam("tenantName") String tenant,
        @PathParam("groupName") String groupName,
        @PathParam("userName") String userName) {
        return call(tenant, () -> {
            dispatcher.getProxy(TenantsBusinessInterface.class).addUserToGroup(userName, groupName);
            return Response.ok().build();
        });
    }

    @DELETE
    @Path("/{groupName}/users/{userName}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response removeUserFromGroup(
        @PathParam("tenantName") String tenant,
        @PathParam("groupName") String groupName,
        @PathParam("userName") String userName) {
        return call(tenant, () -> {
            dispatcher.getProxy(TenantsBusinessInterface.class).removeUserFromGroup(userName, groupName);
            return Response.ok().build();
        });
    }
}
