package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfAssociations;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.AssociationService;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.AssociationItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Objects;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes/{uuid}/associations")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class AssociationResource extends AbstractResource {

    @Inject
    AssociationService associationService;

    @GET
    @Operation(operationId = "listNodeAssociations", summary = "List all node associations")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of associations of the specified node is returned", content = @Content(schema = @Schema(implementation = PageOfAssociations.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listNodeAssociations(
        @PathParam("uuid") String uuid,
        @Parameter(
            description = "Specify the kind of relationship to filter: if not specified no filter is applied",
            example = "PARENT",
            schema = @Schema(implementation = String.class, enumeration = {"PARENT","CHILD", "SOURCE", "TARGET"})
        )
        @QueryParam("relationship") RelationshipKind relationship,
        @Parameter(
            description = "List of common separated association types to filter",
            examples = @ExampleObject(
                name = "Example association",
                summary = "An association filter example",
                description = "Return only associations having type cm:contains",
                value = "[\"cm:contains\"]"
            )
        )
        @QueryParam("associationTypes") List<String> filterAssociationTypes,
        @Parameter(
            description = "List of common separated node types to filter",
            examples = @ExampleObject(
                name = "Example node",
                summary = "A node filter example",
                description = "Return only associations whose target node has type cm:content",
                value = "[\"cm:content\"]"
            )
        )
        @QueryParam("nodeTypes") List<String> filterNodeTypes,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> Response.ok(
            associationService.findAssociations(uuid, relationship, flat(filterAssociationTypes), flat(filterNodeTypes), pageable)
        ).build());
    }

    @POST
    @Operation(operationId = "linkNode", summary = "Link two nodes by a new association")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "The new association is created",
            content = @Content(schema = @Schema(implementation = AssociationItem.class))
        ),
        @APIResponse(
            responseCode = "202",
            description = "The association request has been submitted",
            content = @Content(schema = @Schema(implementation = AsyncOperation.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response linkNode(
        @PathParam("uuid") String uuid,
        @Parameter(description = "Operation mode", schema = @Schema(implementation = String.class, enumeration = {"SYNC","ASYNC","AUTO"}))
        @QueryParam("mode") @DefaultValue("SYNC") String operationMode,
        @RequestBody(
            description = "New link details",
            required = true,
            content = @Content(schema = @Schema(implementation = LinkItemRequest.class))
        ) LinkItemRequest association) {
        var mode = validateAndGet(() -> OperationMode.valueOf(operationMode));
        validate((() -> {
            Objects.requireNonNull(association);

            if (association.getRelationship() != null) {
                switch (association.getRelationship()) {
                    case PARENT, SOURCE:
                        break;
                    default:
                        throw new BadRequestException("Unsupported relationship " + association.getRelationship());
                }
            }
        }));
        return call(() -> {
            AsyncOperation<AssociationItem> f = associationService.linkNode(uuid, association, mode);
            if (f.isCompleted()) {
                var r = f.get();
                return Response.created(
                    UriBuilder.fromResource(AssociationResource.class)
                        .path(StringUtils.equals(uuid, r.getParent()) ? r.getChild() : r.getParent())
                        .build(uuid)
                ).entity(r).build();
            } else {
                return f.isFailed() ? Response.serverError().entity(f).build() : Response.accepted(f).build();
            }
        });
    }

    @GET
    @Path("/{target}")
    @Operation(operationId = "getAssociation", summary = "Get a node association")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The specified association is returned", content = @Content(schema = @Schema(implementation = AssociationItem.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getAssociation(
        @PathParam("uuid") String uuid,
        @PathParam("target") String targetUUID) {

        return call(() -> Response.ok(associationService.findAssociation(uuid, targetUUID)).build());
    }

    @DELETE
    @Path("/{target}")
    @Operation(operationId = "unlinkNode", summary = "Delete a node association (unlink). If a node become unreachable it is deleted.")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The specified association is deleted"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response unlinkNode(
        @PathParam("uuid") String uuid,
        @PathParam("target") String targetUUID,
        @QueryParam("type") String associationType,
        @QueryParam("association") String associationName,
        @Parameter(description = "Relationship of target to uuid", schema = @Schema(implementation = String.class, enumeration = {"PARENT","CHILD","SOURCE","TARGET"}))
        @QueryParam("relationship") @DefaultValue("PARENT") RelationshipKind relationship) {

        var link = new LinkItemRequest();
        link.setTypeName(associationType);
        link.setName(associationName);
        link.setVertexUUID(targetUUID);
        link.setRelationship(relationship);

        return call(() -> {
            associationService.unlinkNode(uuid, link);
            return Response.noContent().build();
        });
    }

    @PATCH
    @Path("/{target}")
    @Operation(operationId = "renameAssociation", summary = "Rename a node association")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The specified association is renamed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response renameAssociation(
        @PathParam("uuid") String uuid,
        @PathParam("target") String targetUUID,
        @Parameter(description = "Relationship of target to uuid", schema = @Schema(implementation = String.class, enumeration = {"PARENT","CHILD"}))
        @QueryParam("relationship") @DefaultValue("PARENT") RelationshipKind relationship,
        @RequestBody(
            description = "Editable fields of the association",
            required = true,
            content = @Content(schema = @Schema(implementation = RenameAssociationRequest.class))
        ) RenameAssociationRequest association
    ) {
        validate((() -> {
            Objects.requireNonNull(association);
            Objects.requireNonNull(association.getName());
        }));

        final String parentUUID, childUUID;
        childUUID = switch (relationship) {
            case CHILD -> {
                parentUUID = uuid;
                yield targetUUID;
            }
            case PARENT -> {
                parentUUID = targetUUID;
                yield uuid;
            }
            default -> throw new BadRequestException("Invalid relationship: " + relationship);
        };

        return call(() -> {
            associationService.renameAssociation(parentUUID, childUUID, association.getName());
            return Response.noContent().build();
        });
    }

    @Getter
    @Setter
    @ToString
    public static class RenameAssociationRequest {
        private String name;
    }
}
