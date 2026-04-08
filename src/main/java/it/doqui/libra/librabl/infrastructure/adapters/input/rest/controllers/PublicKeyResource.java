package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.user.PkItem;
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
    AuthenticationManagerPort authenticationManagerPort;

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
        return call(() -> Response.ok(authenticationManagerPort.listPublicKeys()).build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addPublicKey", summary = "Add a public key to the tenant or updates the specified existent one")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Created or Updated Public Key is returned",
            content = @Content(schema = @Schema(implementation = PkItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "409", description = "Duplicated key"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response addOrUpdatePublicKey(@RequestBody(
        description = "New key data or key changes",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = PkItem.class)
        )) PkItem item) {

        return call(() -> {
            var result = authenticationManagerPort.addOrUpdatePublicKey(item);
            var resultURI = UriBuilder
                .fromResource(PublicKeyResource.class)
                .path(result.getKid())
                .build();
            return Response.created(resultURI).entity(result).build();
        });
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
            var deleted = authenticationManagerPort.deletePublicKey(kid);
            return deleted ? Response.noContent().build() : Response.status(404).build();
        });
    }
}
