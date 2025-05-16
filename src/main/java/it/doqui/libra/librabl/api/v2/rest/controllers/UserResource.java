package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfUsers;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.UserService;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.ListContainer;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.security.DetailedUserItem;
import it.doqui.libra.librabl.views.security.EditableUserDescriptor;
import it.doqui.libra.librabl.views.security.UserItem;
import it.doqui.libra.librabl.views.security.UserRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.Getter;
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

import java.net.URI;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/users")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class UserResource extends AbstractResource {

    @Inject
    UserService userService;

    @GET
    @Operation(operationId = "findUsers", summary = "Find users")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of users is returned", content = @Content(schema = @Schema(implementation = PageOfUsers.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response findUsers(
        @Parameter(
            description = "Username prefix to filter"
        )
        @QueryParam("username") String username,
        @Parameter(description = "Specify if metadata must be included in query result", example = "false")
        @QueryParam("metadata") @DefaultValue("true") boolean includeMetadata,
        @Parameter(description = "If true usernames are returned without the tenant part", example = "false")
        @QueryParam("nameOnly") @DefaultValue("false") boolean nameOnly,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> Response.ok(userService.findUsers(StringUtils.stripToEmpty(username) + "*", includeMetadata, nameOnly, pageable)).build());
    }

    @POST
    @Operation(operationId = "createUser", summary = "Create a new user")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "The user has been created",
            content = @Content(schema = @Schema(implementation = UserItem.class))),
        @APIResponse(
            responseCode = "200",
            description = "The requested users have been created",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserItem.class))),
        @APIResponse(responseCode = "409", description = "User already exists"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response createUser(
        @RequestBody(
            description = "User descriptor",
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = UserRequest.class))
        )
        OneOrMoreUserRequest body) {
        return call(() -> {
            if (body.getItems() == null) {
                UserItem createdUser = userService.createUser(body);
                URI resultURI = UriBuilder
                    .fromResource(UserResource.class)
                    .path(AuthorityRef.valueOf(createdUser.getUsername()).getIdentity())
                    .build();

                return Response.created(resultURI).entity(createdUser).build();
            } else {
                var users = userService.createUsers(body.getItems());
                return Response.ok(users).build();
            }
        });
    }

    @Getter
    public static class OneOrMoreUserRequest extends UserRequest implements ListContainer<UserRequest> {

        private List<UserRequest> items;
    }

    @DELETE
    @Path("/{username}")
    @Operation(operationId = "deleteUser", summary = "Delete the requested user")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @RolesAllowed(UserContext.ROLE_ADMIN)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The user has been deleted"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteUser(
        @Parameter(description = "The user to delete")
        @PathParam("username") String username
    ) {
        return call(() -> {
            userService.deleteUser(username);
            return Response.noContent().build();
        });
    }

    @PATCH
    @Path("/{username}")
    @Operation(operationId = "updateUser", summary = "Update the specified user")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @RolesAllowed(UserContext.ROLE_ADMIN)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The user has been updated"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updateUser(
        @Parameter(description = "The user to update")
        @PathParam("username") String username,
        @RequestBody EditableUserDescriptor item
        ) {
        return call(() -> {
            userService.updateUser(username, item);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/{username}")
    @Operation(operationId = "getUser", summary = "Get the specified user")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The user is retrieved", content = @Content(schema = @Schema(implementation = DetailedUserItem.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getUser(
        @Parameter(description = "The user to find")
        @PathParam("username") String username
    ) {
        return call(() -> Response.ok(userService.findUser(username)).build());
    }

//    @PATCH
//    @Operation(operationId = "createMultipleUsers", summary = "Perform multiple user creations")
//    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
//    @Consumes({MediaType.APPLICATION_JSON_PATCH_JSON, MediaType.APPLICATION_JSON})
//    @APIResponses(value = {
//        @APIResponse(
//            responseCode = "200",
//            description = "The specified operations have been performed",
//            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserItem.class))
//        ),
//        @APIResponse(responseCode = "403", description = "Permission denied"),
//        @APIResponse(responseCode = "500", description = "Unexpected error")
//    })
//    public Response createMultipleUsers(
//        @RequestBody(
//            description = "Array of user requests",
//            required = true,
//            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserRequest.class))
//        )
//        List<NodeOperation> operations
//    ) {
//        return call(() -> {
//            return Response.status(501).build();
//        });
//    }
}
