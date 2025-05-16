package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.MimeTypeService;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.mimetype.MimeTypeItem;
import it.doqui.libra.librabl.views.mimetype.MimeTypeRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/mimetypes")
@Slf4j
public class MimeTypeResource extends AbstractResource {

    @Inject
    MimeTypeService mimeTypeService;

    @POST
    @Operation(operationId = "addMimeType", summary = "Adds a mimetype if it doesn't exist")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Operation successfully completed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response addMimeType(@RequestBody MimeTypeRequest item) {
        return call(() -> {
            mimeTypeService.addAll(List.of(item));
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/{id}")
    @Operation(operationId = "getMimeType", summary = "Get a MimeType by id")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Return the requested MimeType item",
            content = @Content(schema = @Schema(implementation = MimeTypeItem.class))
        ),
        @APIResponse(responseCode = "404", description = "MimeType not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response getMimeType(@PathParam("id") long id) {
        return call(() -> mimeTypeService.getById(id)
            .map(m -> Response.ok(m).build())
            .orElse(Response.status(404).build()));
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "deleteMimeType", summary = "Deletes a mimetype")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Operation successfully completed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response deleteMimeType(@PathParam("id") long id) {
        return call(() -> {
            mimeTypeService.deleteById(id);
            return Response.noContent().build();
        });
    }

    @DELETE
    @Operation(operationId = "deleteMimeTypes", summary = "Deletes a list of mimetypes")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Operation successfully completed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response deleteMimeTypes(
        @Parameter(
            description = "list of mimetype IDs (also separated by commas)",
            schema = @Schema(type = SchemaType.ARRAY, implementation = Long.class)
        )
        @QueryParam("id") List<String> ids) {
        return call(() -> {
            mimeTypeService.delete(ObjectUtils.map(flat(ids), Long::parseLong));
            return Response.noContent().build();
        });
    }

    @PATCH
    @Operation(operationId = "addMimeTypes", summary = "Adds a list of mimetypes ignoring existing ones")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Operation successfully completed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response addMimeTypes(@RequestBody List<MimeTypeRequest> items) {
        return call(() -> {
            mimeTypeService.addAll(items);
            return Response.noContent().build();
        });
    }

    @PUT
    @Operation(operationId = "replaceMimeTypes", summary = "Replaces all mimetypes with the supplied ones")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Operation successfully completed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response replaceMimeTypes(@RequestBody List<MimeTypeRequest> items) {
        return call(() -> {
            mimeTypeService.replaceAll(items);
            return Response.noContent().build();
        });
    }

    @GET
    @Operation(operationId = "listMimetypes", summary = "Find mimetypes")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Return the found mimetypes",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = MimeTypeItem.class))),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listMimetypes(
        @Parameter(
            description = "Extension to find",
            example = "xls"
        )
        @QueryParam("ext") String ext,
        @Parameter(
            description = "Mimetype to find",
            example = "image/png"
        )
        @QueryParam("mimetype") String mimetype,
        @QueryParam("includeStarExtensions") @DefaultValue("false") boolean includeStarExtensions
    ) {
        return call(() -> {
            MimeTypeItem mt = new MimeTypeItem();
            mt.setMimetype(StringUtils.stripToNull(mimetype));
            mt.setFileExtension(StringUtils.stripToNull(ext));
            List<MimeTypeItem> mimetypes = mimeTypeService.list(mt, includeStarExtensions);
            return Response.ok(mimetypes).build();
        });
    }
}
