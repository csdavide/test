package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.impl.DtoMapper;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.MassiveDeleteNodeRequest;
import it.doqui.libra.librabl.api.v1.rest.dto.Node;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.util.List;

@Path("/v1/tenants/{tenantName}/nodes/_massive")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class MassiveResource extends AbstractBridgeResource {

    @Inject
    DtoMapper dtoMapper;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response massiveCreateContent(@PathParam("tenantName") String tenant, @TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(tenant, () -> {
            var parentNodeUids = dtoMapper.convert(getStringFromMultipart("parentNodeUids", input, true), String[].class);
            if (!(parentNodeUids.length > 0)) {
                throw new BadRequestException("parentNodeUids must contain at least one element");
            }

            var nodesRequests = dtoMapper.convert(getStringFromMultipart("nodes", input, true), Node[].class);
            if (!(nodesRequests.length > 0)) {
                throw new BadRequestException("nodes must contain at least one element");
            }

            if (parentNodeUids.length != nodesRequests.length) {
                throw new BadRequestException("number of parentNodeUids does not match number of nodes");
            }

            var oldImplementation = BooleanUtils.toBoolean(getStringFromMultipart("oldImplementation", input, false));
            var synchronousReindex = BooleanUtils.toBoolean(getStringFromMultipart("synchronousReindex", input, false));
            var contents = getMultipleByteArraysFromMultipart("binaryContent", input, 50);
            var nodes = dispatcher.getProxy(NodesBusinessInterface.class).massiveCreateNode(parentNodeUids, nodesRequests, contents, oldImplementation, synchronousReindex);
            return Response.ok(nodes).build();
        });
    }

    @POST
    @Path("/metadata")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response massiveGetContentMetadata(@PathParam("tenantName") String tenant, List<String> uids) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(NodesBusinessInterface.class).massiveGetContentMetadata(uids)).build());
    }

    @PUT
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response massiveUpdateMetadata(@PathParam("tenantName") String tenant, List<Node> nodes) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).massiveUpdateMetadata(nodes);
            return Response.ok().build();
        });
    }

    @POST
    @Path("/contents")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response massiveRetrieveContentData(@PathParam("tenantName") String tenant, List<Node> nodes) {
        return call(tenant, () -> {
            var file = dispatcher.getProxy(NodesBusinessInterface.class).massiveRetrieveContentData(nodes);
            return Response.ok(file)
                .type(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"contents.zip\"")
                .header("Content-Length", file.length())
                .build();
        });
    }

    @DELETE
    @Path("/contents")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public Response massiveDeleteNode(@PathParam("tenantName") String tenant, MassiveDeleteNodeRequest massiveDeleteNodeRequest) {
        return call(tenant, () -> {
            var uids = massiveDeleteNodeRequest.getUids();
            if (uids == null) {
                throw new BadRequestException("Null uids");
            }

            if (massiveDeleteNodeRequest.getAction() == null) {
                throw new BadRequestException("Null action");
            }

            dispatcher.getProxy(NodesBusinessInterface.class).massiveDeleteNode(uids, massiveDeleteNodeRequest.getAction());
            return Response.ok().build();
        });
    }
}
