package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfNodes;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.ArchiveService;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.node.NodeItem;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/archived-nodes")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class ArchivedNodeResource extends AbstractResource {

    @Inject
    ArchiveService archiveService;

    @GET
    @Path("/{uuid}")
    @Operation(operationId = "getArchivedNode", summary = "Get an archived node")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A detailed node is returned", content = @Content(schema = @Schema(implementation = NodeItem.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Node not found in the archive"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getNode(
        @PathParam("uuid") String uuid,
        @Parameter(
            description = "List of comma separated property names to filter in"
        )
        @QueryParam("properties") List<String> filterPropertyNames,
        @Parameter(
            description = "List of comma separated options to alter the result layout",
            schema = @Schema(type = SchemaType.ARRAY, implementation = String.class, enumeration = {"DEFAULT", "SYS_PROPERTIES", "PARENT_ASSOCIATIONS", "PARENT_HARD_ASSOCIATIONS", "SG", "TX", "NO_NULL_PROPERTIES"})
        )
        @QueryParam("options") List<String> options,
        @Parameter(
            description = "Specify the locale to use for localizable multi-languages properties",
            example = "it_IT"
        )
        @QueryParam("locale") String locale
    ) {
        return call(() -> archiveService.getNode(
                uuid,
                MapOption.valueOf(flat(options)),
                ObjectUtils.asNullableSet(flat(filterPropertyNames)),
                I18NUtils.parseLocale(locale)
            )
            .map(n -> Response.ok(n).build())
            .orElse(Response.status(404).build()));
    }

    @GET
    @Operation(operationId = "findArchivedNodes", summary = "Find archived nodes")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of archived nodes is returned", content = @Content(schema = @Schema(implementation = PageOfNodes.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response findNodes(
        @Parameter(
            description = "List of comma separated UUIDs"
        )
        @QueryParam("uuid") List<String> uuid,
        @Parameter(
            description = "List of comma separated types to use as a filter"
        )
        @QueryParam("types") List<String> types,
        @Parameter(
            description = "List of comma separated required aspects"
        )
        @QueryParam("aspects") List<String> aspects,
        @Parameter(
            description = "List of comma separated property names to filter in"
        )
        @QueryParam("properties") List<String> filterPropertyNames,
        @Parameter(
            description = "List of comma separated options to alter the result layout",
            schema = @Schema(type = SchemaType.ARRAY, implementation = String.class, enumeration = {"DEFAULT", "SYS_PROPERTIES", "PARENT_ASSOCIATIONS", "PARENT_HARD_ASSOCIATIONS", "SG", "TX", "NO_NULL_PROPERTIES"})
        )
        @QueryParam("options") List<String> options,
        @Parameter(
            description = "Specify the locale to use for localizable multi-languages properties",
            example = "it_IT"
        )
        @QueryParam("locale") String locale,
        @QueryParam("metadata") @DefaultValue("true") boolean includeMetadata,
        @QueryParam("excludeDescendants") @DefaultValue("false") boolean excludeDescendants,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> {
            Paged<NodeItem> page = archiveService.findNodes(
                flat(uuid),
                flat(types),
                flat(aspects),
                includeMetadata,
                MapOption.valueOf(flat(options)),
                ObjectUtils.asNullableSet(flat(filterPropertyNames)),
                I18NUtils.parseLocale(locale),
                excludeDescendants,
                pageable
            );
            return Response.ok(page).build();
        });
    }

    @DELETE
    @Path("/{uuid}")
    @Operation(operationId = "purgeNode", summary = "Purge a node")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Node is correctly purged"),
        @APIResponse(responseCode = "401", description = "User not authenticated"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Node or associations not found"),
        @APIResponse(responseCode = "409", description = "Node cannot be archived and active at the same time"),
        @APIResponse(responseCode = "412", description = "Node not purgeable"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response purgeNode(
        @Parameter(description = "Node to purge")
        @PathParam("uuid")
        String uuid,
        @Parameter(description = "It decides if the content file has to be wiped out or not")
        @DefaultValue("false")
        @QueryParam("wipeable")
        boolean wipeable
    ) {
        return call(() -> {
            archiveService.purgeNode(uuid, wipeable);
            return Response.noContent().build();
        });
    }
}
