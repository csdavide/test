package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.SharedLinkService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.share.KeyRequestPayload;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;

import static it.doqui.libra.librabl.utils.RSAUtils.decodePublicKeyFromBase64;
import static it.doqui.libra.librabl.utils.RSAUtils.verify;

@Path("/v2/shares")
@Slf4j
public class SharedLinkResource extends AbstractResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Path("/keys")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listSharedPublicKeys", summary = "List keys used to share links")
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
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response listPublicKeys() {
        return call(() -> Response.ok(sharedLinkService.listPublicKeys()).build());
    }

    @GET
    @Path("/download/{key}")
    @Produces(MediaType.WILDCARD)
    @Operation(operationId = "downloadBySharedLink", summary = "Download a node attachment using a shared link")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The node file attachment has been downloaded"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "426", description = "Link expired or not yet valid"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response downloadBySharedLink(
        @Context @TraceParam(ignore = true) UriInfo uriInfo,
        @HeaderParam("x-forwarded-for") @TraceParam(ignore = true) String xForwardFor,
        @PathParam("key") String key,
        @QueryParam("inline") @DefaultValue("false") boolean inline) {
        return call(() -> {
            final var requestUri = Optional.ofNullable(xForwardFor).orElse(uriInfo.getRequestUri().toString());
            return map(sharedLinkService.streamSharedContentData(requestUri, key), inline);
        });
    }

    @POST
    @Path("/signed-download")
    @Produces(MediaType.WILDCARD)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "downloadByKeyPayload", summary = "Download a node attachment using a signed key")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The node file attachment has been downloaded"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response downloadByKeyPayload(
        @RequestBody(
            description = "Key payload descriptor",
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = KeyRequestPayload.class))) KeyRequestPayload payload) {
        validate(() -> {
            Objects.requireNonNull(payload, "No payload");
            Objects.requireNonNull(payload.getRequest(), "No request in payload");
            var request = payload.getRequest();
            if (request.getValidUntil() != null) {
                if (request.getValidUntil() < System.currentTimeMillis()) {
                    throw new PreconditionFailedException("Invalid expiration");
                }
            }
        });

        var request = payload.getRequest();
        try {
            var plainText = String.format("%s|%s|%s|%s",
                StringUtils.stripToEmpty(request.getContentPropertyName()),
                StringUtils.stripToEmpty(request.getPublicKey()),
                StringUtils.stripToEmpty(request.getTenant()),
                StringUtils.stripToEmpty(request.getUuid()));
            log.debug("Verifying request '{}'", plainText);

            byte[] signature = new Base64().decode(payload.getSignature());
            PublicKey publicKey = decodePublicKeyFromBase64(request.getPublicKey());
            if (!verify(plainText, signature, publicKey)) {
                throw new BadRequestException("Invalid signature");
            }
        } catch (Exception e) {
            throw new BadRequestException("Unable to verify signature: " + e.getMessage());
        }

        return call(() -> map(sharedLinkService.streamSharedContentData(request), false));
    }

    private Response map(NodeAttachment a, boolean inline) {
        var mimeType = IOUtils.mimeType(a.getContentProperty().getMimetype());
        var disposition = a.formatDisposition(inline);
        log.debug("Returning content {} with mimeType {} and disposition {}", a.getContentProperty(), mimeType, disposition);
        return Response.ok(a.getFile())
            .type(mimeType)
            .header("Content-Disposition", disposition)
            .build();
    }
}
