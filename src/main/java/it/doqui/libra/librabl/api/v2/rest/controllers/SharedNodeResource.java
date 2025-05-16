package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.SharedLinkService;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.Location;
import it.doqui.libra.librabl.views.share.SharingItem;
import it.doqui.libra.librabl.views.share.SharingRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes/{uuid}/shares")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class SharedNodeResource extends AbstractResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Operation(operationId = "listSharingItems", summary = "List all shared link item of the specified node")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "List of shared link items",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SharingItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listSharingItems(@PathParam("uuid") String uuid) {
        return call(() -> Response.ok(sharedLinkService.listSharingItems(uuid)).build());
    }

    @POST
    @Operation(operationId = "shareNodeContent", summary = "Create a new shared link on a specified node content")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "The shared link has been created",
            content = @Content(schema = @Schema(implementation = Location.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response shareNodeContent(
        @PathParam("uuid") String uuid,
        @RequestBody SharingRequest sharingRequest
        ) {
        return call(() -> {
            var result = sharedLinkService.shareNodeContent(uuid, sharingRequest);
            var elements = result.split("/");
            var resultURI = UriBuilder
                .fromResource(SharedNodeResource.class)
                .path(elements[elements.length - 1])
                .build(uuid);

            return Response.created(resultURI).entity(new ImmutablePair<>("url", result)).build();
        });
    }

    @PUT
    @Path("/{key}")
    @Operation(operationId = "updateSharedLink", summary = "Update a shared link")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The shared link has been updated"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified key is not available"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updateSharedLink(
        @PathParam("uuid") String uuid,
        @PathParam("key") String key,
        @RequestBody SharingRequest sharingRequest) {
        return call(() -> {
            sharedLinkService.updateSharedLink(uuid, key, sharingRequest);
            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{key}")
    @Operation(operationId = "deleteSharedLink", summary = "Delete a shared link")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The shared link has been removed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified key is not available"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteSharedLink(@PathParam("uuid") String uuid, @PathParam("key") String key) {
        return call(() -> {
            sharedLinkService.removeSharedLink(uuid, key);
            return Response.noContent().build();
        });
    }

    @DELETE
    @Operation(operationId = "disableAllSharedLinks", summary = "Disable all shared link of a node")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The shared links have been disabled"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The node is not shared"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response disableAllSharedLinks(@PathParam("uuid") String uuid) {
        return call(() -> {
            sharedLinkService.removeAllSharedLinks(uuid);
            return Response.noContent().build();
        });
    }
}
