package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.application.model.management.SystemStatusInfo;
import it.doqui.libra.librabl.application.ports.in.IntegrityUseCase;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.AsyncOperationService;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.ManagementService;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.MgmtOperation;
import it.doqui.libra.librabl.infrastructure.platform.stats.StatMeasure;
import it.doqui.libra.librabl.infrastructure.platform.stats.StatService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/management")
@Slf4j
public class ManagementResource extends AbstractResource {

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "libra.vm.domain", defaultValue = "csi.it")
    String hostDomain;

    @Inject
    ManagementService managementService;

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    StatService statService;

    @Inject
    IntegrityUseCase integrityUseCase;

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSystemStatusInfo", summary = "Get status information")
    @Traceable(ignore = true)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "System status information is returned",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = SystemStatusInfo.class)
            )
        ),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getSystemStatusInfo(
        @QueryParam("expected") @DefaultValue("0") int expected,
        @QueryParam("timeout") @DefaultValue("30000") long timeout,
        @QueryParam("format") @DefaultValue("json") String format) {
        return call(() -> {
            var ssi = integrityUseCase.checkSystemStatus(expected, timeout);
            if (ssi == null) {
                throw new SystemException("No system status received");
            }

            var result = switch (format) {
                case "text" -> {
                    if (ssi.getExpectedCount() == 1 && ssi.isOk()) {
                        yield "1";
                    }

                    char[] status = new char[Integer.max(ssi.getExpectedCount(), ssi.getFeedbacks())];
                    Arrays.fill(status, '0');

                    for (int i = 0; i < ssi.getInstances().size(); i++) {
                        var instance = ssi.getInstances().get(i);
                        if (!instance.isOk()) {
                            continue;
                        }

                        var name = instance.getName().split("_")[0];
                        var p = name.lastIndexOf('-');
                        if (p != -1) {
                            try {
                                var num = Integer.parseInt(name.substring(p + 1));
                                if (num > 0 && num <= status.length) {
                                    status[num - 1] = '1';
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }

                    var s = new StringBuilder();
                    for (char c : status) {
                        s.append(c);
                    }

                    yield s.toString();
                }
                case "json" -> ssi;
                default -> throw new BadRequestException("Unsupported format: " + format);
            };
            return Response.ok(result).build();
        });
    }

    @GET
    @Path("/cxf/wsdl")
    @Produces(MediaType.APPLICATION_XML)
    @Operation(operationId = "getWSDL", summary = "Get WSDL")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "CXF Service WSDL is returned"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getWSDL(@QueryParam("node") boolean node, @QueryParam("exp") String exp) {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(String.format("http://localhost:%d/cxf/streamingWS?wsdl", port)))
            .GET()
            .build();

        try {
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            var body = response.body();

            String target = null;
            if (StringUtils.isBlank(exp) && node) {
                exp = "node";
            }

            if (exp != null) {
                if (Strings.CS.equals(exp, "node")) {
                    var hostname = bootstrapper.getHostName();
                    if (!Strings.CS.endsWith(hostname, "." + hostDomain)) {
                        hostname += "." + hostDomain;
                    }

                    target = "http://" + hostname + ":" + port;
                } else {
                    String finalExp = exp;
                    target = ConfigProvider.getConfig()
                        .getOptionalValue("libra.cxf.endpoints." + StringUtils.stripToEmpty(exp), String.class)
                        .orElseThrow(() -> new BadRequestException("Invalid exp parameter: " + finalExp));
                }
            }

            if (target != null) {
                var sb = new StringBuilder(body);
                int p0 = -1;
                do {
                    p0 = sb.indexOf("location=\"", p0 + 1);
                    if (p0 > -1) {
                        int p1 = sb.indexOf("/cxf", p0);
                        if (p1 > -1) {
                            sb.replace(p0, p1, "location=\"" + target);
                            body = sb.toString();
                        }
                    }
                } while (p0 > -1);
            }

            return Response.status(response.statusCode()).entity(body).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SystemException(e);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getPackageVersion", summary = "Get package version information")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Package version information is returned"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getPackageVersion() {
        var map = new HashMap<Object,Object>(managementService.getBootAttributes());
        var runtime = Runtime.getRuntime();
        map.put("heapSize", runtime.totalMemory());
        map.put("heapMaxSize", runtime.maxMemory());
        map.put("heapFreeSize", runtime.freeMemory());

        return Response.ok(map).build();
    }

    @GET
    @Path("/stats")
    @Operation(operationId = "getStats", summary = "Get statistics")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Statistics are returned", content = @Content(schema = @Schema(implementation = StatMeasure.class))),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getStats() {
        return Response.ok(statService.getAggregatedStatMeasure()).build();
    }

    @PATCH
    @Path("/operations")
    @Consumes(MediaType.APPLICATION_JSON_PATCH_JSON)
    @Operation(operationId = "performOperations", summary = "Perform management operations")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The specified operations have been performed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response performOperations(
        @RequestBody(
            description = "Array of operations",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = MgmtOperation.class))
        )
        List<MgmtOperation> operations
    ) {
        return call(() -> {
            log.debug("Got operations {}", operations);
            managementService.performOperations(operations);
            return Response.noContent().build();
        });
    }

    @PATCH
    @Path("/tenants/{tenant}/operations")
    @Consumes(MediaType.APPLICATION_JSON_PATCH_JSON)
    @Operation(operationId = "performTenantOperations", summary = "Perform management operations on the specified tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The specified operations have been performed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response performTenantOperations(
        @PathParam("tenant") String tenant,
        @RequestBody(
            description = "Array of operations",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = MgmtOperation.class))
        )
        List<MgmtOperation> operations
    ) {
        return call(() -> {
            log.debug("Got operations {} on tenant {}", operations, tenant);
            managementService.performOperations(tenant, operations);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/tenants/{tenant}/operations/{id}")
    @Operation(operationId = "getTenantAsyncOperation", summary = "Get async operation status")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Operation status returned", content = @Content(schema = @Schema(implementation = FeedbackAsyncOperation.class))),
        @APIResponse(responseCode = "404", description = "Operation not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getAsyncOperation(@PathParam("tenant") String tenant, @PathParam("id") String taskId) {
        return call(() -> asyncOperationService
            .getTask(tenant, taskId)
            .map(op -> Response.ok(op).build())
            .orElseThrow(() -> new NotFoundException(taskId)));
    }
}

