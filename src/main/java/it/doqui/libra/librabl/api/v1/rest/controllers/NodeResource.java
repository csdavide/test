package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.*;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Path("/v1/tenants/{tenantName}/nodes")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class NodeResource extends AbstractBridgeResource {

    @GET
    @Path("/{uid}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getContentMetadata(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(NodesBusinessInterface.class).getContentMetadata(uuid)).build());
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response createContent(@PathParam("tenantName") String tenant,@TraceParam(ignore = true) MultipartFormDataInput input) {
        try {
            return call(tenant, () -> {
                var parentNodeUid = getStringFromMultipart("parentNodeUid", input, true);
                var node = objectMapper.readValue(getInputStreamFromMultipart("node", input), Node.class);
                var bytes = getByteArraysFromMultipart("binaryContent", input, false);
                var createdUid = dispatcher.getProxy(NodesBusinessInterface.class).createContent(parentNodeUid, node, bytes);
                return Response.ok(new Data(createdUid)).build();
            });
        } finally {
            input.close();
        }
    }

    @POST
    @Path("/_log")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response createContentLog(@PathParam("tenantName") String tenant, byte[] body) {
        try {
            log.debug("Got multipart\n{}", new String(body, StandardCharsets.UTF_8));
            return Response.accepted().build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PUT
    @Path("/{uid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response updateMetadata(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid, Node node) {
        return call(tenant, () -> {
            validate(() -> Objects.requireNonNull(node, "Null node"));
            if (node.getUid() != null && !(uuid.equals(node.getUid()))) {
                throw new BadRequestException("uid in path doesn't match uid in Node object");
            }

            dispatcher.getProxy(NodesBusinessInterface.class).updateMetadata(uuid, node);
            return Response.ok().build();
        });
    }

    @DELETE
    @Path("/{uid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public Response deleteNode(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid, DeleteNodeAction action) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).deleteNode(uuid, action);
            return Response.ok().build();
        });
    }

    @PUT
    @Path("/{uid}/_restore")
    @Operation(hidden = true)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response restoreContent(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> {
            dispatcher.getProxy(NodesBusinessInterface.class).restoreContent(uuid);
            return Response.ok().build();
        });
    }

    @GET
    @Path("/{uid}/paths")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getPaths(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> Response.ok(dispatcher.getProxy(NodesBusinessInterface.class).getPaths(uuid)).build());
    }

    @POST
    @Path("/_search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response luceneSearch(
        @PathParam("tenantName") String tenant,
        @QueryParam("metadata") Boolean metadata,
        @QueryParam("metadataItems") String metadataItems,
        SearchParams searchParams) {
        return call(tenant, () -> {
            if (searchParams == null) {
                throw new BadRequestException("Null searchParams");
            }

            if (searchParams.getLuceneQuery() == null) {
                throw new BadRequestException("Null luceneQuery in searchParams");
            }

            var _metadataItems = metadataItems;
            if (metadata != null && metadata) {
                if (_metadataItems == null) {
                    _metadataItems = "*";
                }
            }

            var result = dispatcher.getProxy(NodesBusinessInterface.class).luceneSearch(searchParams, _metadataItems);
            return Response.ok(result).build();
        });
    }

    @PUT
    @Path("/{uid}/_rename")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response renameContent(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        RenameContentRequest renameParams) {
        return call(tenant, () -> {
            if (renameParams == null) {
                throw new BadRequestException("Null renameParams");
            }

            if (renameParams.getNameValue() == null) {
                throw new BadRequestException("Null nameValue");
            }

            if (renameParams.isOnlyPrimaryAssociation() == null) {
                throw new BadRequestException("Null onlyPrimaryAssociation");
            }

            dispatcher.getProxy(NodesBusinessInterface.class).renameContent(uuid, renameParams);
            return Response.ok().build();
        });
    }

    @GET
    @Path("/{uid}/db")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getDbIdFromUID(@PathParam("tenantName") String tenant, @PathParam("uid") String uuid) {
        return call(tenant, () -> {
           var dbId = dispatcher.getProxy(NodesBusinessInterface.class).getDbIdFromUID(uuid);
           return Response.ok(new Data(dbId)).build();
        });
    }

    @POST
    @Path("/_list/deleted")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response listDeletedNodes(
        @PathParam("tenantName") String tenant,
        @QueryParam("metadata") Boolean metadata,
        NodeArchiveParams nodeArchiveParams) {
        return call(tenant, () -> {
            if (nodeArchiveParams == null) {
                throw new BadRequestException("Null nodeArchiveParams");
            }

            var result = dispatcher.getProxy(NodesBusinessInterface.class)
                .listDeletedNodes(nodeArchiveParams, metadata);
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/_compare")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response compareDigest(
        @PathParam("tenantName") String tenant,
        @QueryParam("algorithm") String algorithm,
        CompareDigestRequest compareDigestRequest) {
        return call(tenant, () -> {
            var result = dispatcher.getProxy(NodesBusinessInterface.class)
                .compareDigest(compareDigestRequest, algorithm);
            return Response.ok(new Data(result)).build();
        });
    }

    @POST
    @Path("/{parentNodeUid}/_fromTempTenant/{tempNodeUid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response createContentFromTemp(
        @PathParam("tenantName") String tenant,
        @PathParam("parentNodeUid") String parentNodeUid,
        @PathParam("tempNodeUid") String tempNodeUid,
        Node node) {
        return call(tenant, () -> {
            if (node == null) {
                throw new BadRequestException("Null node");
            }

            var result = dispatcher.getProxy(NodesBusinessInterface.class)
                .createContentFromTemp(parentNodeUid, tempNodeUid, node);
            return Response.ok(new Data(result)).build();
        });
    }
}
