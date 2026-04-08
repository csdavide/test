package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.doqui.libra.librabl.application.model.graph.*;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.ports.in.SearchUseCase;
import it.doqui.libra.librabl.application.ports.in.MultipleNodeOperationUseCase;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.policy.*;
import it.doqui.libra.librabl.foundation.*;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.async.TxSupport;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.AsyncOperationService;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.NodeOperation;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.NodeOperationResponse;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.OperationParameters;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.FilterParameters;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.ListOfPaths;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.PageOfNodes;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.query.QueryParameters;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class NodeResource extends AbstractResource {

    @Inject
    NodeUseCase nodeService;

    @Inject
    SearchUseCase searchService;

    @Inject
    MultipleNodeOperationUseCase multipleNodeOperationService;

    @Inject
    AsyncOperationService asyncOperationService;

    @GET
    @Path("/{uuid}")
    @Operation(operationId = "getNodeMetadata", summary = "Get a detailed node")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A detailed node is returned", content = @Content(schema = @Schema(implementation = NodeItem.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getNodeMetadata(
        @PathParam("uuid") String uuid,
        @BeanParam FilterParameters filterParameters)
    {
        return call(() -> nodeService
                .getNodeMetadata(
                        new Vertex(VertexType.UUID, uuid),
                        MapOption.valueOf(filterParameters.getOptions()),
                        ObjectUtils.asNullableSet(flat(filterParameters.getFilterPropertyNames())),
                        I18NUtils.parseLocale(filterParameters.getLocale())
                )
                .map(node -> Response.ok(node).build())
                .orElseThrow(() -> new NotFoundException(uuid)));
    }

    @GET
    @Path("/{uuid}/paths")
    @Operation(operationId = "listNodePaths", summary = "List all node paths")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A list of paths of the specified node is returned", content = @Content(schema = @Schema(implementation = ListOfPaths.class))),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listNodePaths(@PathParam("uuid") String uuid) {
        return call(() -> Response.ok(new ItemList<>(nodeService.listNodePaths(uuid))).build());
    }

    @GET
    @Operation(operationId = "findNodes", summary = "Find nodes")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of nodes is returned", content = @Content(schema = @Schema(implementation = PageOfNodes.class))),
        @APIResponse(responseCode = "400", description = "Bad request"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed({UserContext.ROLE_USER, UserContext.ROLE_SYSMON})
    public Response findNodes(
        @BeanParam QueryParameters queryParameters,
        @BeanParam FilterParameters filterParameters,
        @Parameter(
            description = "sort query result",
            examples = @ExampleObject(
                name = "Example 1",
                summary = "A sort example",
                description = "Sort by ascending name and descending modified date",
                value = "[\"cm:name\",\"-cm:modified\"]"
            )
        )
        @QueryParam("sortBy") List<String> sortBy,
        @Parameter(description = "specify if metadata must be included in query result", example = "false")
        @QueryParam("metadata") @DefaultValue("true") boolean includeMetadata,
        @Valid @BeanParam Pageable pageable) {
        return call(() -> {
            var options = MapOption.valueOf(flat(filterParameters.getOptions()));
            if (CollectionUtils.isEmpty(queryParameters.getUuids())) {
                String q = buildQuery(queryParameters);
                List<SortDefinition> sortFields = sortBy == null
                    ? List.of()
                    :sortBy.stream()
                    .map(s -> Strings.CS.startsWith(s, "-")
                        ? SortDefinition.builder().fieldName(s.substring(1)).ascending(false).build()
                        : SortDefinition.builder().fieldName(s).ascending(true).build()
                    )
                    .collect(Collectors.toList());

                return Response.ok(
                    includeMetadata
                        ? searchService.findNodes(q, sortFields,
                        options,
                        ObjectUtils.asNullableSet(flat(filterParameters.getFilterPropertyNames())),
                        I18NUtils.parseLocale(filterParameters.getLocale()), pageable)
                        : searchService.findNodes(q, sortFields, pageable)
                ).build();
            } else {
                options.add(MapOption.ACL);
                Paged<NodeItem> page = new Paged<>(nodeService.listNodeMetadata(
                    flat(queryParameters.getUuids()),
                    options,
                    ObjectUtils.asNullableSet(flat(filterParameters.getFilterPropertyNames())),
                    I18NUtils.parseLocale(filterParameters.getLocale()),
                    QueryScope.DEFAULT));
                return Response.ok(page).build();
            }
        });
    }

    private String buildQuery(QueryParameters queryParameters) {
        var conditions = new ArrayList<String>();
        if (StringUtils.isNotBlank(queryParameters.getQ())) {
            conditions.add(queryParameters.getEncoding() == QueryParameters.EncodingType.NONE ? queryParameters.getQ() : new String(Base64.getUrlDecoder().decode(queryParameters.getQ())));
        }

        if (StringUtils.isNotBlank(queryParameters.getPath())) {
            conditions.add(String.format("PATH:\"%s\"", queryParameters.getPath()));
        }

        if (StringUtils.isNotBlank(queryParameters.getRoute())) {
            conditions.add(String.format("NODEPATH:\"%s\"", queryParameters.getRoute()));
        }

        if (conditions.isEmpty()) {
            throw new BadRequestException("Either q or uuid or path or route must be filled");
        }

        return conditions
            .stream()
            .map(c -> Strings.CS.contains(c, " AND ") ? String.format("(%s)", c) : c)
            .collect(Collectors.joining(" AND "));
    }

    @POST
    @Path("/searches")
    @Operation(operationId = "searchNodes", summary = "Search nodes")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of nodes is returned", content = @Content(schema = @Schema(implementation = PageOfNodes.class))),
        @APIResponse(responseCode = "400", description = "Bad request"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed({UserContext.ROLE_USER, UserContext.ROLE_SYSMON})
    public Response searchNodes(
        @RequestBody QueryRequest queryRequest) {
        return findNodes(
            queryRequest.getQueryParameters(),
            queryRequest.getFilterParameters(),
            queryRequest.getSortBy(),
            queryRequest.isMetadata(),
            queryRequest.getPaging()
        );
    }

    @Setter
    public static class QueryRequest {
        private String q;

        @JsonProperty("uuid")
        @JsonAlias("uuids")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> uuids;
        private QueryParameters.EncodingType encoding;
        private String path;
        private String route;

        private List<String> filterPropertyNames;
        private List<String> options;

        @Getter
        private List<String> sortBy;

        @Getter
        private List<String> locale;

        @Getter
        private boolean metadata;

        private Pageable paging;

        @JsonIgnore
        public QueryParameters getQueryParameters() {
            var queryParameters = new QueryParameters();
            queryParameters.setQ(q);
            queryParameters.setUuids(uuids);
            queryParameters.setEncoding(encoding);
            queryParameters.setPath(path);
            queryParameters.setRoute(route);
            return queryParameters;
        }

        @JsonIgnore
        public FilterParameters getFilterParameters() {
            var filterParameters = new FilterParameters();
            filterParameters.setFilterPropertyNames(filterPropertyNames);
            filterParameters.setOptions(options);
            return filterParameters;
        }

        @JsonIgnore
        public Pageable getPaging() {
            var p = paging;
            if (p == null) {
                p = new Pageable();
                p.setSize(50);
            }

            return p;
        }
    }

    @POST
    @Operation(operationId = "createNode", summary = "Create a node")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "201",
            description = "Created UUID is returned",
            content = @Content(schema = @Schema(implementation = Identifier.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response createNode(
        @RequestBody(
            description = "Node descriptor",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = LinkedInputNodeRequest.class))
        )
        OneOrMoreLinkedInputNodeRequest body
        ) {
        return call(() -> {
            if (body.getItems() == null) {
                var uuid = nodeService.createNode(body);
                var resultURI = UriBuilder
                    .fromResource(NodeResource.class)
                    .path(uuid)
                    .build();

                return Response.created(resultURI).entity(NodeInfoItem.builder().uuid(uuid).build()).build();
            } else {
                var uuids = nodeService.createNodes(body.getItems(), Set.of());
                return Response
                    .ok(uuids
                        .stream()
                        .map(uuid -> NodeInfoItem.builder().uuid(uuid).build())
                        .toList()
                    )
                    .build();
            }
        });
    }

    @PATCH
    @Path("/{uuid}")
    @Operation(operationId = "updateNode", summary = "Update node metadata")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node has been updated"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updateNode(
        @PathParam("uuid") String uuid,
        @Parameter(
            description = "List of comma separated options to alter the behaviour",
            schema = @Schema(type = SchemaType.ARRAY, implementation = String.class, enumeration = {"HANDLE_CONTENT_PROPERTIES", "DISCARD_UNKOWN_PRESENT_METADATA"})
        )
        @QueryParam("options")
        List<String> options,
        @RequestBody(content = @Content(schema = @Schema(implementation = InputNodeRequest.class)))
        InputNodeRequest body
    ) {
        return call(() -> {
            nodeService.updateNode(uuid, body, ObjectUtils.valueOf(OperationOption.class, options));
            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{uuid}")
    @Operation(operationId = "deleteNode", summary = "Delete a node")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node has been deleted"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteNode(
        @PathParam("uuid") String uuid,
        @Parameter(description = "It specifies the level of deletion", schema = @Schema(implementation = String.class, enumeration = {"DELETE","PURGE","PURGE_COMPLETE"}))
        @QueryParam("mode") @DefaultValue("DELETE") String deleteMode) {
        var mode = validateAndGet(() -> DeleteMode.valueOf(deleteMode));
        return call(() -> {
            nodeService.deleteNode(uuid, mode);
            return Response.noContent().build();
        });
    }

    @DELETE
    @Operation(operationId = "deleteNodes", summary = "Delete nodes matching query parameters")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The found nodes have been deleted"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response deleteNodes(
        @BeanParam QueryParameters queryParameters,
        @QueryParam("mode") @DefaultValue("DELETE") String deleteMode) {
        var mode = validateAndGet(() -> DeleteMode.valueOf(deleteMode));
        return call(() -> {
            multipleNodeOperationService.deleteNodes(queryParameters, mode);
            return Response.noContent().build();
        });
    }

    @PATCH
    @Operation(operationId = "performNodeOperations", summary = "Perform node operations")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @Consumes({MediaType.APPLICATION_JSON_PATCH_JSON})
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "The specified operations have been performed",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = NodeOperationResponse.class))
        ),
        @APIResponse(
            responseCode = "202",
            description = "The request has been submitted",
            content = @Content(schema = @Schema(implementation = AsyncOperation.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response performNodeOperations(
        @Parameter(description = "Operation mode", schema = @Schema(implementation = String.class, enumeration = {"SYNC","ASYNC","AUTO"}))
        @QueryParam("mode") @DefaultValue("SYNC") String operationMode,
        @QueryParam("delay") @DefaultValue("0") long delay,
        @Parameter(description = "Transaction support", schema = @Schema(implementation = String.class, enumeration = {"NONE","REQUIRED"}))
        @QueryParam("txSupport") @DefaultValue("REQUIRED") TxSupport txSupport,
        @RequestBody(
            description = "Array of operations",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = NodeOperation.class))
        )
        List<NodeOperation> operations
    ) {
        var mode = validateAndGet(() -> OperationMode.valueOf(operationMode));
        return call(() -> {
            log.debug("Got operations {}", operations);
            var op = asyncOperationService.performOperations(operations, OperationParameters.builder().delay(delay).mode(mode).txSupport(txSupport).build());
            if (op.isCompleted()) {
                return Response.ok(op.getResult()).build();
            } else if (op.isFailed()) {
                return Response.serverError().entity(op).build();
            } else {
                return Response.accepted(op).build();
            }
        });
    }

    @Getter
    public static class OneOrMoreLinkedInputNodeRequest extends LinkedInputNodeRequest implements ListContainer<LinkedInputNodeRequest> {

        @SuppressWarnings("unused")
        private List<LinkedInputNodeRequest> items;
    }

    /*
    @Getter
    @Setter
    public static class MultipartNodeCreationRequest {

        @FormParam("descriptor")
        @PartType(MediaType.APPLICATION_JSON)
        private LinkedInputNodeRequest descriptor;

        @FormParam("content")
        @PartType(MediaType.WILDCARD)
        private InputStream content;
    }
     */

}
