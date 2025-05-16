package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.ListOfRenditionNodes;
import it.doqui.libra.librabl.api.v2.rest.dto.ListOfTransformerNodes;
import it.doqui.libra.librabl.api.v2.rest.dto.RenditionGenerationMode;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.RenditionService;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.node.ContentRef;
import it.doqui.libra.librabl.views.node.ContentRequest;
import it.doqui.libra.librabl.views.renditions.RenditionNode;
import it.doqui.libra.librabl.views.renditions.RenditionSettings;
import it.doqui.libra.librabl.views.renditions.TransformerIdentifiedInputRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes/{xml}/transformers")
@RolesAllowed(UserContext.ROLE_USER)
public class RenditionTransformerResource extends AbstractResource {

    @Inject
    RenditionService renditionService;

    @GET
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @Operation(operationId = "getRenditionTransformers", summary = "Retrieves a list of rendition transformers of the given renditionable node")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "List of rendition transformers is returned", content = @Content(schema = @Schema(implementation = ListOfTransformerNodes.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or transformer nodes not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getRenditionTransformers(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String uuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("contentPropertyName") String contentPropertyName
    ) {
        return call(() -> Response.ok(renditionService.findRenditionTransformers(new ContentRequest(uuid, contentPropertyName))).build());
    }

    @GET
    @Path("/{rt}/renditions")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @Operation(operationId = "getNodeRenditions", summary = "Retrieves a list of renditions of the given renditonable node and the specified transformer node associated")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "List of renditions is returned", content = @Content(schema = @Schema(implementation = ListOfRenditionNodes.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or transformer node or rendition nodes not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getNodeRenditions(
        @Parameter(description = "uuid of renditionable node")
        @PathParam("xml") String xmlUuid,
        @Parameter(description = "uuid of transformer node")
        @PathParam("rt") String rtUuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("xmlContentPropertyName") String xmlContentPropertyName,
        @Parameter(description = "contentPropertyName of the transformer node")
        @QueryParam("rtContentPropertyName") String rtContentPropertyName,
        @Parameter(description = "It specifies the kind of renditions")
        @QueryParam("generated") Boolean generated
    ) {
        return call(() -> Response.ok(
            renditionService.findRenditionNodes(
                new ContentRequest(xmlUuid, xmlContentPropertyName),
                new ContentRequest(rtUuid, rtContentPropertyName),
                generated,
                false)
        ).build());
    }

    //todo: in REST ci sarà la possibilità di creare transformer senza associarli a nessun nodo? Ad oggi 28/02/2025 non esiste (più) tale servizio
    @POST
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @Operation(operationId = "createAndAssignTransformer", summary = "Create a transformer node assigning it to the given renditonable node")
    @APIResponses(value = {
        @APIResponse(responseCode = "201", description = "Transformer created and assigned"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node not found"),
        @APIResponse(responseCode = "412", description = "Cannot assign transformer"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response createAndAssignTransformer(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String uuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("contentPropertyName") String contentPropertyName,
        @RequestBody(
            description = "Transformer descriptor",
            required = true,
            content = @Content(schema = @Schema(implementation = TransformerIdentifiedInputRequest.class))
        ) TransformerIdentifiedInputRequest transformerRequest
    ) {
        return call(() -> {
            var transformerNode = renditionService.createAndAssignTransformer(new ContentRequest(uuid, contentPropertyName), transformerRequest);
            var uri = UriBuilder.fromResource(RenditionTransformerResource.class).path(transformerNode.getUuid()).build(uuid);
            return Response.created(uri).entity(transformerNode).build();
        });
    }

    @POST
    @Path("/{rt}/renditions")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @Operation(
        operationId = "createRendition",
        description = "Create rendition given the renditionable node, the transformer node and metadata of the new rendition node, possibly assigning directly the content of a node"
    )
    @APIResponses(value = {
        @APIResponse(responseCode = "201", description = "Rendition node created and assigned correctly"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or transformer node not found"),
        @APIResponse(responseCode = "412", description = "Nodes involved have not the aspects or properties required"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response createRendition(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String xmlUuid,
        @Parameter(description = "uuid of the transformer node")
        @PathParam("rt") String rtUuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("xmlContentPropertyName") String xmlContentPropertyName,
        @Parameter(description = "contentPropertyName of the transformer node")
        @QueryParam("rtContentPropertyName") String rtContentPropertyName,
        @Parameter(
            description = "It specifies if the rendition has to be assigned, generated, or generation has to be forced",
            schema = @Schema(implementation = String.class, enumeration = {"ASSIGN", "GENERATE", "GENERATE_FORCED"}),
            required = true
        ) @QueryParam("mode") String mode,
        @RequestBody(
            description = "Settings for renditions' generation or assignation",
            content = @Content(schema = @Schema(implementation = RenditionSettings.class)),
            required = true
        ) RenditionSettings renditionSettings
    ) {
        return call(() -> {
            var generationMode = RenditionGenerationMode.valueOf(mode);
            if (generationMode.equals(RenditionGenerationMode.GENERATE_FORCED)) {
                renditionSettings.setForceGeneration(true);
            }

            RenditionNode renditionNode;
            if (generationMode == RenditionGenerationMode.ASSIGN) {
                renditionNode = renditionService.setNodeRendition(
                    new ContentRequest(xmlUuid, xmlContentPropertyName),
                    new ContentRequest(rtUuid, rtContentPropertyName),
                    renditionSettings.getNewRenditionInputRequest());
            } else {
                renditionNode = renditionService.generateRendition(
                    new ContentRef().setUuid(xmlUuid).setContentPropertyName(xmlContentPropertyName),
                    new ContentRef().setUuid(rtUuid).setContentPropertyName(rtContentPropertyName),
                    renditionSettings
                );
            }
            var uri = UriBuilder.fromResource(RenditionTransformerResource.class).path(renditionNode.getUuid()).build(xmlUuid, rtUuid);
            return Response.created(uri).entity(renditionNode).build();
        });
    }

    @DELETE
    @Path("/{rt}")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @Operation(
        operationId = "deleteTransformer",
        summary = "Delete the logical association between the given renditionable node and the given transformer node, possibly deleting the transformer node"
    )
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Association between renditionable and transformer node and/or transformer node deleted correctly"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or transformer node not found"),
        @APIResponse(responseCode = "412", description = "Nodes involved have not the aspects or properties required"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteTransformer(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String xmlUuid,
        @Parameter(description = "uuid of the transformer node")
        @PathParam("rt") String rtUuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("xmlContentPropertyName") String xmlContentPropertyName,
        @Parameter(description = "contentPropertyName of the transformer node")
        @QueryParam("rtContentPropertyName") String rtContentPropertyName
    ) {
        return call(() -> {
            renditionService.deleteTransformer(new ContentRequest(xmlUuid, xmlContentPropertyName), new ContentRequest(rtUuid, rtContentPropertyName));
            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{rt}/renditions/{rd}")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @Operation(operationId = "removeRendition", summary = "Delete the specified rendition node associated with the specified renditionable node and the specified transformer node")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Rendition node has been deleted successfully"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or transformer node or rendition node not found"),
        @APIResponse(responseCode = "412", description = "Node involved have not the aspects or properties required"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response removeRendition(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String xmlUuid,
        @Parameter(description = "uuid of the transformer node")
        @PathParam("rt") String rtUuid,
        @Parameter(description = "uuid of the rendition uuid")
        @PathParam("rd") String rdUuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("xmlContentPropertyName") String xmlContentPropertyName,
        @Parameter(description = "contentPropertyName of the transformer node")
        @QueryParam("rtContentPropertyName") String rtContentPropertyName,
        @Parameter(description = "contentPropertyName of the rendition node")
        @QueryParam("rdContentPropertyName") String rdContentPropertyName
    ) {
        return call(() -> {
            renditionService.deleteRendition(
                new ContentRequest(xmlUuid, xmlContentPropertyName),
                new ContentRequest(rtUuid, rtContentPropertyName),
                new ContentRequest(rdUuid, rdContentPropertyName)
            );
            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{rt}/renditions")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @Operation(operationId = "removeRenditions", summary = "Delete all the rendition nodes associated with the specified renditionable node and the specified transformer node")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Rendition nodes have been deleted successfully"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "Renditionable node or transformer node not found"),
        @APIResponse(responseCode = "412", description = "Node involved have not the aspects or properties required"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response removeRenditions(
        @Parameter(description = "uuid of the renditionable node")
        @PathParam("xml") String xmlUuid,
        @Parameter(description = "uuid of the transformer node")
        @PathParam("rt") String rtUuid,
        @Parameter(description = "contentPropertyName of the renditionable node")
        @QueryParam("xmlContentPropertyName") String xmlContentPropertyName,
        @Parameter(description = "contentPropertyName of the transformer node")
        @QueryParam("rtContentPropertyName") String rtContentPropertyName,
        @Parameter(description = "It specifies the kind of renditions to delete")
        @QueryParam("generated") Boolean generated
        ) {
        return call(() -> {
            renditionService.deleteRenditions(
                new ContentRequest(xmlUuid, xmlContentPropertyName),
                new ContentRequest(rtUuid, rtContentPropertyName),
                generated
            );
            return Response.noContent().build();
        });
    }
}
