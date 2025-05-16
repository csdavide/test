package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.AssociationsSearchParams;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.api.v1.rest.dto.ModifyAssociationJobRequest;
import it.doqui.libra.librabl.api.v1.rest.dto.ModifyAssociationRequest;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tenants/{tenantName}/nodes/{uid}/associations")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class AssociationResource extends AbstractBridgeResource {

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getAssociations(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("associationType") String associationType,
        AssociationsSearchParams searchParams) {
        return call(tenant, () -> {
            if (associationType == null) {
                throw new BadRequestException("Null associationType");
            }

            var _associationType = associationType.toUpperCase();
            switch (_associationType) {
                case "CHILD", "PARENT", "SOURCE", "TARGET":
                    break;

                default:
                    throw new BadRequestException("associationType param not in [PARENT,CHILD,SOURCE,TARGET]");
            }

            var result = dispatcher.getProxy(NodesBusinessInterface.class).getAssociations(uuid, _associationType, searchParams);
            return Response.ok(result).build();
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response modifyAssociation(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        ModifyAssociationRequest associationInfo) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).modifyAssociation(uuid, associationInfo);
            return Response.ok().build();
        });
    }

    @POST
    @Path("/_async")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response modifyAssociationJob(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        ModifyAssociationJobRequest associationInfo) {
        return call(tenant, () -> {
            var result = dispatcher.getProxy(NodesBusinessInterface.class).modifyAssociationJob(uuid, associationInfo);
            return Response.ok(new Data(result)).build();
        });
    }
}
