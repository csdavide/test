package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.AuthenticationService;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.security.PkRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Path("/v2/keys")
@Slf4j
@RolesAllowed(UserContext.ROLE_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
public class PublicKeyResource extends AbstractResource {

    @Inject
    AuthenticationService authenticationService;

    @GET
    @Operation(operationId = "listPublicKeys", summary = "List public keys of the tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "List of public keys is returned",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = PkItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listPublicKeys() {
        return call(() -> Response.ok(authenticationService.listPublicKeys()).build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addPublicKey", summary = "Add a public key to the tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "Created Public Key is returned",
            content = @Content(schema = @Schema(implementation = PkItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "409", description = "Duplicated key"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response addPublicKey(@RequestBody PkRequest request) {
        return call(() -> {
            var item = authenticationService.addPublicKey(request);
            var resultURI = UriBuilder
                .fromResource(PublicKeyResource.class)
                .path(item.getKid())
                .build();
            return Response.created(resultURI).entity(item).build();
        });
    }

    @PUT
    @Path("/{kid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updatePublicKey", summary = "Update a public key")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Updated Public Key is returned",
            content = @Content(schema = @Schema(implementation = PkItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "409", description = "Duplicated key"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updatePublicKey(
        @PathParam("kid") String kid,
        @RequestBody(
            description = "Key changes",
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PkRequest.class))
        ) PkItem item) {
        item.setKid(kid);
        return call(() -> Response.ok(authenticationService.updatePublicKey(item)).build());
    }

    @DELETE
    @Path("/{kid}")
    @Operation(operationId = "removePublicKey", summary = "Remove a public key from the tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The specified public key has been removed"),
        @APIResponse(responseCode = "404", description = "No key deleted"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response removePublicKey(@PathParam("kid") String kid) {
        return call(() -> {
            var deleted = authenticationService.deletePublicKey(kid);
            return deleted ? Response.noContent().build() : Response.status(404).build();
        });
    }
}
