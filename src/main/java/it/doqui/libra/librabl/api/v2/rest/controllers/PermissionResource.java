package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.PermissionService;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.acl.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/v2/nodes/{uuid}/permissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class PermissionResource extends AbstractResource {

    @Inject
    PermissionService permissionService;

    @GET
    @Operation(operationId = "listNodePermissions", summary = "Retrieves permissions information from the node")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Node permissions are returned", content = @Content(schema = @Schema(implementation = PermissionsResponse.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listNodePermissions(
        @PathParam("uuid") String uuid,
        @Parameter(description = "Kind of permissions returned", schema = @Schema(implementation = String.class, enumeration = {"NONE","NODE","ALL"}))
        @QueryParam("kind") @DefaultValue("ALL") PermissionService.PermissionKind kind,
        @Parameter(description = "If true reports permission rights in a readable format")
        @QueryParam("readable") @DefaultValue("false") boolean readable) {
        return call(Response.ok(permissionService.listPermissions(uuid, kind, readable))::build);
    }

    @POST
    @Operation(operationId = "addNodePermissions", summary = "Add permissions to the node")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Permissions has been added"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified security group is not available"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response addNodePermissions(
        @PathParam("uuid") String uuid,
        @RequestBody(required = true) @TraceParam(ignore = true) PermissionsList input) {
        return call(() -> {
            permissionService.addPermissions(uuid, input.getPermissions());
            return Response.noContent().build();
        });
    }

    @PUT
    @Operation(operationId = "replaceNodePermissions", summary = "Replace node permissions. If the inheritance flag is specified, it is replaced too")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Permissions has been replaced"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified security group is not available"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response replaceNodePermissions(
        @PathParam("uuid") String uuid,
        @RequestBody(required = true) @TraceParam(ignore = true) PermissionsDescriptor input) {
        return call(() -> {
            permissionService.replacePermissions(uuid, input.getPermissions(), Optional.ofNullable(input.getInheritance()));
            return Response.noContent().build();
        });
    }

    @PATCH
    @Operation(operationId = "setNodeInheritance", summary = "Change node inheritance")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Permission inheritance has been changed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified security group is not available"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response setNodeInheritance(
        @PathParam("uuid") String uuid,
        @RequestBody(required = true) @TraceParam(ignore = true) InheritanceRequest input) {
        return call(() -> {
            var inheritance = Optional.ofNullable(input.getInheritance()).orElseThrow(() -> new BadRequestException("No inheritance specified"));
            permissionService.setInheritance(uuid, inheritance);
            return Response.noContent().build();
        });
    }

    @DELETE
    @Operation(operationId = "deleteNodePermissions", summary = "Delete node permissions")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Permissions has been replaced"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified security group is not available"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteNodePermissions(
        @PathParam("uuid") String uuid,
        @Parameter(description = "Authority to delete")
        @QueryParam("authority") String authority,
        @Parameter(description = "Rights of the specified authority to remove. If no rights are specified all the authority permissions will be removed")
        @QueryParam("rights") List<String> rights,
        @RequestBody(description = "Permissions to delete") @TraceParam(ignore = true) PermissionsList input) {
        return call(() -> {
            if (input != null) {
                if (StringUtils.isNotBlank(authority) || CollectionUtils.isEmpty(rights)) {
                    throw new BadRequestException("Incompatible parameters");
                }

                permissionService.removePermissions(uuid, input.getPermissions());
            } else if (StringUtils.isBlank(authority)) {
                throw new BadRequestException("No authority to remove");
            } else if (CollectionUtils.isEmpty(flat(rights))) {
                permissionService.removeAllAuthorityPermissions(uuid, authority);
            } else {
                var permissions = rights.stream().map(r -> {
                    var p = new PermissionItem();
                    p.setAuthority(authority);
                    p.setRights(r);
                    return p;
                }).collect(Collectors.toList());
                permissionService.removePermissions(uuid, permissions);
            }

            return Response.noContent().build();
        });
    }
}
