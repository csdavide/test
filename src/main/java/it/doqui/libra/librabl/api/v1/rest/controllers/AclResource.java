package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.AclRecord;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;

@Path("/v1/tenants/{tenantName}/nodes/{uid}/acls")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class AclResource extends AbstractBridgeResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response addAcl(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid, List<AclRecord> acls) {
        return call(tenant, () -> {
            validate(acls);
            dispatcher.getProxy(NodesBusinessInterface.class).addAcl(uuid, acls.toArray(new AclRecord[0]));
            return Response.ok().build();
        });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response updateAcl(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("resetInherits") Boolean resetInherits,
        List<AclRecord> acls) {
        return call(tenant, () -> {
            validate(acls);

            if (resetInherits == null) {
                throw new BadRequestException("Empty resetInherits");
            }

            var aclRecords = acls.toArray(new AclRecord[0]);
            var businessComponent = dispatcher.getProxy(NodesBusinessInterface.class);
            if (resetInherits) {
                businessComponent.updateAcl(uuid, aclRecords);
            } else {
                businessComponent.changeAcl(uuid, aclRecords);
            }

            return Response.ok().build();
        });
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response removeAcl(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        List<AclRecord> acls) {
        return call(tenant, () -> {
            validate(acls);
            dispatcher.getProxy(NodesBusinessInterface.class).removeAcl(uuid, acls.toArray(new AclRecord[0]));
            return Response.ok().build();
        });
    }

    @DELETE
    @Path("/_reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response resetAcl(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        AclRecord filter) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).resetAcl(uuid, filter);
            return Response.ok().build();
        });
    }

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response listAcl(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("showInherited") Boolean showInherited) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(NodesBusinessInterface.class).listAcl(uuid, showInherited)).build());
    }

    @GET
    @Path("/inheritance")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response isInheritsAcl(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> Response.ok(new Data(dispatcher.getProxy(NodesBusinessInterface.class).isInheritsAcl(uuid))).build());
    }

    @PUT
    @Path("/inheritance")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response setInheritsAcl(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid, @QueryParam("inherits") Boolean inherits) {
        return call(tenant, () -> {
            if (inherits == null) {
                throw new BadRequestException("Null inherits");
            }

            dispatcher.getProxy(NodesBusinessInterface.class).setInheritsAcl(uuid, inherits);
            return Response.ok().build();
        });
    }

    private void validate(List<AclRecord> acls) {
        if (acls == null) {
            throw new BadRequestException("Null acls");
        }

        if (acls.isEmpty()) {
            throw new BadRequestException("Empty acls");
        }
    }
}
