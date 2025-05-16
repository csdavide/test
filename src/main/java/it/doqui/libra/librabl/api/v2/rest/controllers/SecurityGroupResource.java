package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.NamedItem;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfSecurityGroups;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.SecurityGroupService;
import it.doqui.libra.librabl.foundation.Named;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.views.acl.EditableSecurityGroup;
import it.doqui.libra.librabl.views.acl.PermissionsList;
import it.doqui.libra.librabl.views.acl.SecurityGroupItem;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.net.URI;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/security-groups")
@RolesAllowed(UserContext.ROLE_USER)
@Slf4j
public class SecurityGroupResource extends AbstractResource {

    @Inject
    SecurityGroupService sgService;

    @GET
    @Operation(operationId = "findSecurityGroups", summary = "Find security groups")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of security nodes is returned", content = @Content(schema = @Schema(implementation = PageOfSecurityGroups.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response findSecurityGroups(
        @Parameter(description = "If specified, filter on name prefix")
        @QueryParam("name") String namePrefix,
        @Parameter(description = "If true reports permission rights in a readable format")
        @QueryParam("readable") @DefaultValue("false") boolean readable,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> Response.ok(sgService.find(namePrefix, readable, pageable)).build());
    }

    @GET
    @Path("/{sgid}")
    @Operation(operationId = "getSecurityGroup", summary = "Get a security groups")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Security node details are returned", content = @Content(schema = @Schema(implementation = SecurityGroupItem.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Security Group not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getSecurityGroup(
        @PathParam("sgid") String sgID,
        @Parameter(description = "If true reports permission rights in a readable format")
        @QueryParam("readable") @DefaultValue("false") boolean readable) {
        return call(() -> sgService.findByUUID(sgID, readable)
            .map(sg -> Response.ok(sg).build())
            .orElseThrow(() -> new NotFoundException(sgID)));
    }

    @PATCH
    @Path("/{sgid}")
    @RolesAllowed({UserContext.ROLE_POWERUSER, UserContext.ROLE_ADMIN})
    @Operation(operationId = "updateSecurityGroup", summary = "Update a security group")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The security group has been updated"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Security Group not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updateSecurityGroup(
        @PathParam("sgid") String sgid,
        @RequestBody(required = true, content = @Content(schema = @Schema(implementation = Named.class)))
        NamedItem named) {
        return call(() -> {
            sgService.rename(sgid, named.getName());
            return Response.noContent().build();
        });
    }

    @PUT
    @Path("/{sgid}")
    @RolesAllowed({UserContext.ROLE_POWERUSER, UserContext.ROLE_ADMIN})
    @Operation(operationId = "replaceSecurityGroup", summary = "Replace a security group")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The security group has been replaced"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Security Group not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response replaceSecurityGroup(
        @PathParam("sgid") String sgid,
        @RequestBody(content = @Content(schema = @Schema(implementation = EditableSecurityGroup.class)))
        SecurityGroupItem sg) {

        return call(() -> {
            if (StringUtils.isNotBlank(sg.getSgID()) && !StringUtils.equals(sg.getSgID(), sgid)) {
                throw new BadRequestException("UUID in the request body must be empty or equals to the one provided in the URL path");
            }

            sgService.update(sgid, sg);
            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{sgid}")
    @RolesAllowed({UserContext.ROLE_POWERUSER, UserContext.ROLE_ADMIN})
    @Operation(operationId = "addPermissionsToSecurityGroup", summary = "Add permissions to a security group")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The security group has been updated"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Security Group not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response addPermissionsToSecurityGroup(
        @PathParam("sgid") String sgid,
        @RequestBody(content = @Content(schema = @Schema(implementation = PermissionsList.class)))
        PermissionsList p) {
        return call(() -> {
            sgService.addPermissions(sgid, p.getPermissions());
            return Response.noContent().build();
        });
    }

    @POST
    @RolesAllowed({UserContext.ROLE_POWERUSER, UserContext.ROLE_ADMIN})
    @Operation(operationId = "createSecurityGroup", summary = "Create a security group")
    @APIResponses(value = {
        @APIResponse(responseCode = "201", description = "The created security group is returned", content = @Content(schema = @Schema(implementation = SecurityGroupItem.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Security Group not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response createSecurityGroup(
        @Parameter(description = "If true reports permission rights in a readable format")
        @RequestBody(content = @Content(schema = @Schema(implementation = EditableSecurityGroup.class)))
        SecurityGroupItem sg) {

        return call(() -> {
            var result = sgService.create(sg, true);
            URI resultURI = UriBuilder
                .fromResource(SecurityGroupResource.class)
                .path(result.getSgID())
                .build();

            return Response.created(resultURI).entity(result).build();
        });
    }
}
