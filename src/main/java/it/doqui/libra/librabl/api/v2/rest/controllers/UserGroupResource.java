package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.NamedItem;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfStrings;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfUsers;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.UserService;
import it.doqui.libra.librabl.foundation.Named;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
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
import java.util.Objects;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/groups")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class UserGroupResource extends AbstractResource {

    @Inject
    UserService userService;

    @GET
    @Operation(operationId = "findGroups", summary = "Find groups")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A list of group names is returned", content = @Content(schema = @Schema(implementation = PageOfStrings.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response findGroups(
        @Parameter(
            description = "Group name prefix to filter"
        )
        @QueryParam("name") String groupname,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> Response.ok(userService.findGroups(groupname + "*", pageable)).build());
    }

    @GET
    @Path("/{group}/users")
    @Operation(operationId = "findGroupUsers", summary = "Retrieve users in a group")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of group names is returned", content = @Content(schema = @Schema(implementation = PageOfUsers.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response findGroupUsers(
        @PathParam("group") String groupname,
        @QueryParam("metadata") @DefaultValue("true") boolean includeMetadata,
        @Parameter(description = "If true usernames are returned without the tenant part", example = "false")
        @QueryParam("nameOnly") @DefaultValue("false") boolean nameOnly,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> Response.ok(userService.findGroupUsers(groupname, includeMetadata, nameOnly, pageable)).build());
    }

    @POST
    @Operation(operationId = "createGroup", summary = "Create a new group")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "201", description = "The group has been created"),
        @APIResponse(responseCode = "409", description = "Group already exists"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response createGroup(
        @RequestBody(content = @Content(schema = @Schema(implementation = Named.class)))
        @TraceParam(ignore = true) NamedItem group
    ) {
        validate(() -> {
            Objects.requireNonNull(group, "Group cannot be null");
            Objects.requireNonNull(group.getName(), "Group name cannot be null");
        });
        return call(() -> {
            String createdGroupName = userService.createGroup(group.getName());
            URI resultURI = UriBuilder
                .fromResource(UserGroupResource.class)
                .path(createdGroupName)
                .build();

            NamedItem g = new NamedItem();
            g.setName(createdGroupName);
            return Response.created(resultURI).entity(g).build();
        });
    }

    @DELETE
    @Path("/{group}")
    @Operation(operationId = "deleteGroup", summary = "Delete a group")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The group has been deleted"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Group not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response deleteGroup(@PathParam("group") String groupname) {
        return call(() -> {
            userService.deleteGroup(groupname);
            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{group}/users")
    @Operation(operationId = "addUserToGroup", summary = "Add a user to the specified group")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The user has been added to the group"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Group not found"),
        @APIResponse(responseCode = "412", description = "User does not exists"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response addUserToGroup(
        @PathParam("group") String groupname,
        @RequestBody(content = @Content(schema = @Schema(implementation = Named.class)))
        @TraceParam(ignore = true) NamedItem user
    ) {
        validate(() -> {
            Objects.requireNonNull(user, "User cannot be null");
            Objects.requireNonNull(user.getName(), "User name cannot be null");
        });
        return call(() -> {
            try {
                userService.addUserToGroup(user.getName(), groupname);
            } catch (NotFoundException e) {
                if (StringUtils.startsWith(e.getMessage(), "User")) {
                    throw new PreconditionFailedException(e.getMessage());
                }

                throw e;
            }

            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{group}/users/{username}")
    @Operation(operationId = "removeUserFromGroup", summary = "Remove a user from the specified group")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The user has been removed from the group"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Either group or user not found"),
        @APIResponse(responseCode = "412", description = "The specified user is not present in the group"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response removeUserFromGroup(@PathParam("group") String groupname, @PathParam("username") String username) {
        return call(() -> {
            userService.removeUserFromGroup(username, groupname);
            return Response.noContent().build();
        });
    }

}
