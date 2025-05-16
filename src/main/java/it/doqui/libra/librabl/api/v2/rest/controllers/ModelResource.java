package it.doqui.libra.librabl.api.v2.rest.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.ModelService;
import it.doqui.libra.librabl.foundation.ItemList;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/models")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class ModelResource extends AbstractResource {

    @Inject
    ModelService modelService;

    @GET
    @Operation(operationId = "listModels", summary = "List models")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "List of models are returned", content = @Content(schema = @Schema(implementation = TenantResource.ListOfModelEntry.class))),
        @APIResponse(responseCode = "404", description = "Tenant not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response listModels(@QueryParam("commons") @DefaultValue("false") boolean includeAny) {
        return call(() -> Response.ok(new ItemList<>(
                modelService
                    .listModels(includeAny)
                    .stream()
                    .map(m -> TenantResource.ModelEntry.builder()
                        .name(m.getModelName())
                        .description(m.getDescription())
                        .version(m.getVersion())
                        .active(m.isActive()).build())
                    .collect(Collectors.toList())
            ))
            .build());
    }

    @GET
    @Path("/*")
    @Operation(operationId = "getCompleteMergedModel", summary = "Get model details as a merge of all model available in the tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Requested custom model is returned", content = @Content(schema = @Schema(implementation = CustomModelSchema.class))),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getCompleteMergedModel(
        @Parameter(description = "if true all common models are included")
        @QueryParam("commons") @DefaultValue("false") boolean includeAny,
        @Parameter(description = "if true types and aspects include inherited properties. In this case common models are always included")
        @QueryParam("flat") @DefaultValue("false") boolean flat
    ) {
        return call(() -> {
            var result = new CustomModelSchema();
            modelService
                .listModels(flat || includeAny)
                .stream()
                .filter(Objects::nonNull)
                .filter(CustomModelSchema::isActive)
                .forEach(model -> {
                    result.getNamespaces().addAll(model.getNamespaces());
                    result.getImports().addAll(model.getImports());


                    if (flat) {
                        result.getAspects().addAll(model.getAspects().stream().map(a -> modelService.getFlatAspect(a.getName())).toList());
                        result.getTypes().addAll(model.getTypes().stream().map(t -> modelService.getFlatType(t.getName())).toList());
                    } else {
                        result.getAspects().addAll(model.getAspects());
                        result.getTypes().addAll(model.getTypes());
                    }

                    result.getProperties().addAll(model.getProperties());
                    result.getDynamicProperties().addAll(model.getDynamicProperties());
                    result.getAssociations().addAll(model.getAssociations());
                });
            return Response.ok(result).build();
        });
    }

    @GET
    @Path("/{name}")
    @Operation(operationId = "getModel", summary = "Get model details")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Requested custom model is returned", content = @Content(schema = @Schema(implementation = CustomModelSchema.class))),
        @APIResponse(responseCode = "404", description = "Model not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getModel(
        @PathParam("name") String name,
        @QueryParam("exact") @DefaultValue("false") boolean exact) {
        return call(() -> {
            var model = exact
                ? modelService.getModel(name)
                : modelService.getNamespaceSchema(name);

            return model
                .map(m -> Response.ok(m).build())
                .orElse(Response.status(404).build());
        });
    }

    @GET
    @Path("/{name}/internal")
    @Operation(operationId = "getInternalModel", summary = "Get internal model representation")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Requested custom model is returned"),
        @APIResponse(responseCode = "404", description = "Model not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @Produces("text/yaml")
    public Response getInternalModel(
        @PathParam("name") String name) {
        return call(() -> modelService.getModel(name)
            .map(model -> {
                try {
                    var mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    var s = mapper.writeValueAsString(model);
                    return Response.ok(s).type("text/yaml").build();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            })
            .orElse(Response.status(404).build()));
    }

    @GET
    @Path("/{name}/file")
    @Operation(operationId = "getStoredModel", summary = "Get stored model representation")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Requested custom model is returned"),
        @APIResponse(responseCode = "404", description = "Model not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @Produces({"text/yaml", "text/xml"})
    public Response getStoredModel(
        @PathParam("name") String name) {
        return call(() -> modelService.getStoredModel(name)
            .map(m -> Response.ok(m.getData()).type(StringUtils.equalsIgnoreCase("xml", m.getFormat()) ? "text/xml" : "text/yaml").build())
            .orElse(Response.status(404).build()));
    }

    @POST
    @Operation(operationId = "deployModel", summary = "Deploy a custom model")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Model deployed"),
        @APIResponse(responseCode = "404", description = "Tenant not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response deployModel(
        @Parameter(description = "Specifies the format of the model. If not specified try to use the request content type",
            examples = {@ExampleObject("yml"), @ExampleObject("yaml"), @ExampleObject("xml")},
            schema = @Schema(implementation = String.class, enumeration = {"yml", "yaml", "xml"}, defaultValue = "yml")
        )
        @QueryParam("fmt") String format,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @TraceParam(ignore = true) @RequestBody(required = true) InputStream is) {
        return call(() -> {
            var fmt = format;
            if (StringUtils.isBlank(fmt)) {
                var h = Optional.ofNullable(customContentType).orElse(contentType);
                if (h != null) {
                    var hh = h.split("/");
                    fmt = hh[hh.length - 1];
                } else {
                    fmt = "yml";
                }

                switch (fmt) {
                    case "yml":
                    case "yaml":
                    case "xml":
                        break;

                    default:
                        fmt = "yml";
                }
            } else {
                switch (format) {
                    case "yml":
                    case "yaml":
                    case "xml":
                        break;

                    default:
                        throw new BadRequestException("Unable to recognize the content type: please specify fmt param");
                }
            }

            modelService.deployModel(fmt, is);
            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{name}")
    @Operation(operationId = "undeployModel", summary = "Undeploy a tenant model")
    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Model undeployed"),
        @APIResponse(responseCode = "404", description = "Tenant or model not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed(UserContext.ROLE_ADMIN)
    public Response undeployModel(@PathParam("name") String modelName) {
        return call(() -> {
            modelService.undeployModel(modelName);
            return Response.noContent().build();
        });
    }
}
