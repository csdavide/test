package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.api.v2.rest.dto.FilterParameters;
import it.doqui.libra.librabl.api.v2.rest.dto.ListOfPaths;
import it.doqui.libra.librabl.api.v2.rest.dto.PageOfNodes;
import it.doqui.libra.librabl.api.v2.rest.dto.QueryParameters;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.MultipleNodeOperationService;
import it.doqui.libra.librabl.business.service.interfaces.NodeService;
import it.doqui.libra.librabl.business.service.interfaces.SearchService;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.business.service.node.SortDefinition;
import it.doqui.libra.librabl.foundation.ItemList;
import it.doqui.libra.librabl.foundation.ListContainer;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.Identifier;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.node.*;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/nodes")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class NodeResource extends AbstractResource {

    @Inject
    NodeService nodeService;

    @Inject
    SearchService searchService;

    @Inject
    MultipleNodeOperationService multipleNodeOperationService;

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
                uuid,
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
                String q;
                if (StringUtils.isBlank(queryParameters.getQ())) {
                    if (StringUtils.isBlank(queryParameters.getPath())) {
                        throw new BadRequestException("Either q or uuid or path must be filled");
                    }

                    q = String.format("PATH:\"%s\"", queryParameters.getPath());
                } else {
                    q = queryParameters.getEncoding() == QueryParameters.EncodingType.NONE ? queryParameters.getQ() : new String(Base64.getUrlDecoder().decode(queryParameters.getQ()));
                    if (StringUtils.isNotBlank(queryParameters.getPath())) {
                        q += String.format(" AND PATH:\"%s\"", queryParameters.getPath());
                    }
                }

                List<SortDefinition> sortFields = sortBy == null
                    ? List.of()
                    :sortBy.stream()
                    .map(s -> StringUtils.startsWith(s, "-")
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
        private List<String> uuids;
        private QueryParameters.EncodingType encoding;
        private String path;

        List<String> filterPropertyNames;
        List<String> options;

        @Getter
        List<String> sortBy;

        @Getter
        List<String> locale;

        @Getter
        boolean metadata;

        Pageable paging;

        public QueryParameters getQueryParameters() {
            var queryParameters = new QueryParameters();
            queryParameters.setQ(q);
            queryParameters.setUuids(uuids);
            queryParameters.setEncoding(encoding);
            queryParameters.setPath(path);
            return queryParameters;
        }

        public FilterParameters getFilterParameters() {
            var filterParameters = new FilterParameters();
            filterParameters.setFilterPropertyNames(filterPropertyNames);
            filterParameters.setOptions(options);
            return filterParameters;
        }

        public Pageable getPaging() {
            var p = paging;
            if (p == null) {
                p = new Pageable();
                p.setSize(50);
            }

            return p;
        }
    }

    /*
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createNodeWithMultipart(
        @MultipartForm MultipartFormDataInput body
    ) {
        final LinkedInputNodeRequest item;
        try {
            var descriptorParts = body.getFormDataMap().get("descriptor");
            if (descriptorParts != null && !descriptorParts.isEmpty()) {
                var descriptorPart = descriptorParts.get(0);
                var s = descriptorPart.getBodyAsString();
                Objects.requireNonNull(s, "Node descriptor cannot be null");
                item = objectMapper.readValue(s, LinkedInputNodeRequest.class);
            } else {
                throw new it.doqui.libra.librabl.foundation.exceptions.BadRequestException("Missing node descriptor");
            }

            var contentParts = body.getFormDataMap().get("content");
            if (contentParts != null && !contentParts.isEmpty()) {
                var contentPart = contentParts.get(0);
                var cs = new LinkedInputNodeRequest.ContentStream();
                cs.setInputStream(contentPart.getBody(InputStream.class, null));

                var mimeTypes = contentPart.getHeaders().get("Content-Type");
                if (mimeTypes != null && !mimeTypes.isEmpty()) {
                    cs.setMimetype(mimeTypes.get(0));
                } else {
                    cs.setMimetype(MediaType.APPLICATION_OCTET_STREAM);
                }

                cs.setEncoding(StringUtils.stripToEmpty(contentPart.getMediaType().getParameters().get("charset")));

                var dispositions = contentPart.getHeaders().get("Content-Disposition");
                if (dispositions != null && !dispositions.isEmpty()) {
                    String s = dispositions.get(0);
                    int p0 = s.indexOf("filename=");
                    if (p0 >= 0 && p0 + 9 < s.length()) {
                        if (s.charAt(p0 + 9) == '\"') {
                            int p1 = s.indexOf('\"', p0 + 10);
                            if (p1 >= 0) {
                                cs.setFileName(s.substring(p0 + 10, p1));
                            }
                        } else {
                            cs.setFileName(s.substring(p0 + 9));
                        }
                    }
                }

                item.setContentStream(cs);
            } // end if contentParts
        } catch (NullPointerException | IOException e) {
            throw new BadRequestException(e.getMessage());
        }

        return call(() -> {
            var uuid = nodeService.createNode(item);
            var resultURI = UriBuilder
                .fromResource(NodeResource.class)
                .path(uuid)
                .build();

            var result = new NodeInfoItem(null, null, uuid, null);
            return Response.created(resultURI).entity(result).build();
        });
    }
     */

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
            required = true,
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

                var result = new NodeInfoItem(null, null, uuid, null);
                return Response.created(resultURI).entity(result).build();
            } else {
                var uuids = nodeService.createNodes(body.getItems());
                return Response
                    .ok(uuids
                        .stream()
                        .map(uuid -> new NodeInfoItem(null, null, uuid, null))
                        .collect(Collectors.toList())
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
            schema = @Schema(type = SchemaType.ARRAY, implementation = String.class, enumeration = {"HANDLE_CONTENT_PROPERTIES"})
        )
        @QueryParam("options")
        List<String> options,
        @RequestBody(required = true, content = @Content(schema = @Schema(implementation = InputNodeRequest.class)))
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
    @Consumes({MediaType.APPLICATION_JSON_PATCH_JSON, MediaType.APPLICATION_JSON})
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
        @RequestBody(
            description = "Array of operations",
            required = true,
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = NodeOperation.class))
        )
        List<NodeOperation> operations
    ) {
        var mode = validateAndGet(() -> OperationMode.valueOf(operationMode));
        return call(() -> {
            log.debug("Got operations {}", operations);
            var op = multipleNodeOperationService.performOperations(operations, null, mode, delay);
            if (op.isCompleted()) {
                return Response.ok(op.get()).build();
            } else if (op.isFailed()) {
                return Response.serverError().entity(op).build();
            } else {
                return Response.accepted(op).build();
            }
        });
    }

    @Getter
    public static class OneOrMoreLinkedInputNodeRequest extends LinkedInputNodeRequest implements ListContainer<LinkedInputNodeRequest> {

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
