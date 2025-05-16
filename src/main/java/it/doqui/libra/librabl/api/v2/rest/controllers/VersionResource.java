package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.VersionService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.version.VersionItem;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Optional;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes/{uuid}/versions")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class VersionResource extends AbstractResource {

    @Inject
    VersionService versionService;

    @GET
    @Operation(operationId = "listNodeVersions", summary = "List all node versions")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "A list of versions of the specified node is returned",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = VersionItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listNodeVersions(
        @PathParam("uuid") String uuid,
        @Parameter(description = "List of tag to filter (comma separated too)")
        @QueryParam("tag") List<String> tags) {
        return call(() -> Response.ok(versionService.listNodeVersions(uuid, flat(tags))).build());
    }

    @POST
    @Operation(operationId = "createNodeVersion", summary = "Create a new version")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "The created version is returned",
            content = @Content(schema = @Schema(implementation = VersionItem.class))
        ),
        @APIResponse(responseCode = "304", description = "Not modified. No version created"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response createNodeVersion(
        @PathParam("uuid") String uuid,
        @RequestBody @TraceParam(ignore = true) TagRequest tagRequest) {
        var tag = Optional.ofNullable(tagRequest).map(TagRequest::getTag).orElse(null);
        return call(() -> versionService.createNodeVersion(uuid, tag)
            .map(item -> {
                var resultURI = UriBuilder
                    .fromResource(VersionResource.class)
                    .path("" + item.getVersion())
                    .build(uuid);

                return Response.created(resultURI).entity(item);
            })
            .orElse(Response.notModified())
            .build());
    }

    @PATCH
    @Path("/{version}")
    @Operation(operationId = "tagNodeVersion", summary = "Change the tag of a version")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Tag applied"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node or version is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response tagNodeVersion(
        @PathParam("uuid") String uuid,
        @PathParam("version") int version,
        @RequestBody @TraceParam(ignore = true) TagRequest tagRequest) {
        var tag = Optional.ofNullable(tagRequest).map(TagRequest::getTag).orElseThrow(() -> new BadRequestException("No tag specified"));
        return call(() -> {
            versionService.alterTagVersion(uuid, version, tag);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/{version}")
    @Operation(operationId = "getNodeVersion", summary = "Get a version")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "The specified version is returned",
            content = @Content(schema = @Schema(implementation = VersionItem.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node or version is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getNodeVersion(
        @PathParam("uuid") String uuid,
        @PathParam("version") int version,

        @Parameter(
            description = "List of comma separated options to alter the result layout",
            schema = @Schema(
                type = SchemaType.ARRAY,
                implementation = String.class,
                enumeration = {
                    "DEFAULT", "SYS_PROPERTIES", "PARENT_ASSOCIATIONS", "PARENT_HARD_ASSOCIATIONS", "SG",
                    "NO_NULL_PROPERTIES", "NO_PROPERTIES", "VARRAY", "NO_PROPERTIES"
                }
            )
        )
        @QueryParam("options")
        List<String> options,

        @Parameter(
            description = "Specify the locale to use for localizable multi-languages properties",
            example = "it_IT"
        )
        @QueryParam("locale")
        String locale) {
        return call(() -> versionService
            .getNodeVersion(uuid, version, MapOption.valueOf(options), I18NUtils.parseLocale(locale))
            .map(item ->Response.ok(item).build())
            .orElseThrow(() -> new NotFoundException("Version not found")));
    }

    @GET
    @Path("/{version}/content")
    @Produces(MediaType.WILDCARD)
    @Operation(operationId = "retrieveDefaultNodeVersionContent", summary = "Download default content of a versioned node")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The node file attachment has been downloaded"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node has no file attachment"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response retrieveDefaultNodeVersionContent(
        @PathParam("uuid") String uuid,
        @PathParam("version") int version,
        @QueryParam("fileName") String fileName,
        @Parameter(description = "Request for inline content disposition")
        @QueryParam("inline") @DefaultValue("false") boolean inline) {
        try {
            return getNodeContent(uuid, version, null, inline, fileName);
        } catch (PreconditionFailedException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{version}/contents/{contentPropertyName}")
    @Produces(MediaType.WILDCARD)
    @Operation(operationId = "retrieveNodeVersionContent", summary = "Download a versioned node attachment")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The node file attachment has been downloaded"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node has no file attachment"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response retrieveNodeVersionContent(
        @PathParam("uuid") String uuid,
        @PathParam("version") int version,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName,
        @Parameter(description = "Request for inline content disposition")
        @QueryParam("inline") @DefaultValue("false") boolean inline) {
        try {
            return getNodeContent(uuid, version, contentPropertyName, inline, fileName);
        } catch (PreconditionFailedException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private Response getNodeContent(String uuid, int version, String contentPropertyName, boolean inline, String fileName) {
        return call(() -> {
            NodeAttachment a = versionService.getVersionedContent(uuid, version, contentPropertyName, fileName);
            String mimeType = a.getContentProperty().getMimetype();
            try {
                MediaType.valueOf(a.getContentProperty().getMimetype());
            } catch (Exception e) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return Response.ok(a.getFile())
                .type(mimeType)
                .header("Content-Disposition", a.formatDisposition(inline))
                .build();
        });
    }

    @Getter
    @Setter
    @ToString
    public static class TagRequest {
        private String tag;
    }
}
