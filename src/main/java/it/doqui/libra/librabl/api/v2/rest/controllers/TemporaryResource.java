package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.DocumentService;
import it.doqui.libra.librabl.business.service.interfaces.TemporaryService;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.views.node.ContentDescriptor;
import it.doqui.libra.librabl.views.node.ContentRef;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.InputStream;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Path("/v2/temporaries")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class TemporaryResource extends AbstractContentResource {

    @Inject
    TemporaryService temporaryService;

    @Inject
    DocumentService documentService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "createTempNode", summary = "Create a temporary node with ephemeral aspect")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "Created node info is returned",
            content = @Content(schema = @Schema(implementation = ContentRef.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response createTempNode(
        @QueryParam("duration") @DefaultValue("PT24H") String durationString,
        @QueryParam("unwrap") @DefaultValue("false") boolean enveloped,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @HeaderParam("Content-Disposition") String contentDisposition,
        @RequestBody @TraceParam(ignore = true) InputStream body) {
        final Duration duration;
        try {
            duration = Optional.ofNullable(durationString).map(Duration::parse).orElse(Duration.ofHours(24));
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid duration");
        }

        final var _contentType = Optional.ofNullable(StringUtils.stripToNull(customContentType)).orElse(contentType);
        return call(() -> {
            final ContentRef ephemeralContent;
            if (enveloped) {
                var documentStream = documentService.unwrap(body);
                documentStream.setMimeType(_contentType);
                documentStream.setFileName(IOUtils.getFileName(contentDisposition));
                ephemeralContent = temporaryService.createEphemeralNode(documentStream);
            } else {
                var descriptor = new ContentDescriptor();
                descriptor.setMimetype(_contentType);
                descriptor.setFileName(IOUtils.getFileName(contentDisposition));
                ephemeralContent = temporaryService.createEphemeralNode(descriptor, body, duration);
            }

            var resultURI = UriBuilder
                .fromResource(TemporaryResource.class)
                .path(ephemeralContent.getUuid())
                .build();

            return Response.created(resultURI).entity(ephemeralContent).build();
        });
    }
}
