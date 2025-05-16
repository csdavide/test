package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.api.v1.rest.dto.SharingInfo;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tenants/{tenantName}/nodes/{uid}/sharedLinks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class SharedLinkResource extends AbstractBridgeResource {

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getSharingInfos(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(NodesBusinessInterface.class).getSharingInfos(uuid)).build());
    }

    @POST
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response shareDocument(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        SharingInfo sharingInfo) {
        return call(tenant, () -> {
            if (sharingInfo == null) {
                throw new BadRequestException("Null sharingInfo");
            }

            var shareLinkId = dispatcher.getProxy(NodesBusinessInterface.class).shareDocument(uuid, sharingInfo);
            return Response.ok(new Data(shareLinkId)).build();
        });
    }

    @DELETE
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response retainDocument(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).retainDocument(uuid);
            return Response.ok().build();
        });
    }

    @DELETE
    @Path("/{shareLinkId}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response removeSharedLink(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @PathParam("shareLinkId") String shareLinkId
        ) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).removeSharedLink(uuid, shareLinkId);
            return Response.ok().build();
        });
    }

    @PUT
    @Path("/{shareLinkId}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response updateSharedLink(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @PathParam("shareLinkId") String shareLinkId,
        SharingInfo sharingInfo
    ) {
        return call(tenant, () -> {
            if (sharingInfo == null) {
                throw new BadRequestException("Null sharingInfo");
            }

            dispatcher.getProxy(NodesBusinessInterface.class).updateSharedLink(uuid, shareLinkId, sharingInfo);
            return Response.ok().build();
        });
    }


}
