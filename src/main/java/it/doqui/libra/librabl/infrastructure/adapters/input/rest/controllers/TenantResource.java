package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.TenantUseCase;
import it.doqui.libra.librabl.foundation.ItemList;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.application.model.tenant.TenantItem;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/tenants")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class TenantResource extends AbstractResource {

    @Inject
    TenantUseCase tenantUseCase;

    @GET
    @Operation(operationId = "listTenants", summary = "List tenants")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "List of tenants is returned", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantItem.class))),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @PermitAll
    public Response listTenants(
        @Parameter(
            description = "A tenant prefix name to use as a filter",
            example = "ACTA"
        )
        @QueryParam("prefix") String prefix
    ) {
        return call(Response.ok(tenantUseCase.findStartingWith(prefix))::build);
    }

    @GET
    @Path("/{tenant}")
    @Operation(operationId = "getTenant", summary = "Get a tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A page of tenants is returned", content = @Content(schema = @Schema(implementation = TenantRef.class))),
        @APIResponse(responseCode = "404", description = "Tenant not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed({UserContext.ROLE_USER, UserContext.ROLE_ADMIN, UserContext.ROLE_SYSADMIN})
    public Response getTenant(@PathParam("tenant") String tenant) {
        return call(() -> tenantUseCase
            .findByIdOptional(tenant, true)
            .map(t -> Response.ok(t).build())
            .orElseThrow(NotFoundException::new));
    }

    @POST
    @Operation(operationId = "createTenant", summary = "Create or complete creation of a tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The specified tenant has been created", content = @Content(schema = @Schema(implementation = TenantItem.class))),
        @APIResponse(responseCode = "304", description = "No change applied"),
        @APIResponse(responseCode = "400", description = "Invalid tenant name"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response createTenant(@RequestBody TenantCreationRequest t) {
        return call(() -> {
            if (StringUtils.isBlank(t.getTenant())) {
                throw new BadRequestException("No tenant name specified");
            }

            var createdTenant = tenantUseCase.createTenant(t);
            if (createdTenant != null) {
                return Response.ok(createdTenant).build();
            } else {
                return Response.noContent().build();
            }
        });
    }

    @POST
    @Path("/{tenant}")
    @Operation(operationId = "syncTenant", summary = "Synchronize tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.MANAGEMENT)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Tenant synchronized"),
        @APIResponse(responseCode = "404", description = "Tenant not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response syncTenant(@PathParam("tenant") String tenant, @QueryParam("includeAny") @DefaultValue("false") boolean includeAny) {
        return call(() -> {
            try {
                tenantUseCase.syncTenant(TenantRef.valueOf(tenant), includeAny);
            } catch (UnauthorizedException e) {
                throw new NotFoundException(tenant);
            }

            return Response.noContent().build();
        });
    }

    @DELETE
    @Path("/{tenant}")
    @Operation(operationId = "deleteTenant", summary = "Delete tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.MANAGEMENT)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "Tenant deleted"),
        @APIResponse(responseCode = "404", description = "Tenant not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response deleteTenant(@PathParam("tenant") String tenant, @QueryParam("tenant") String confirmationTenant) {
        return call(() -> {
            if (!Strings.CI.equals(tenant, confirmationTenant)) {
                throw new BadRequestException("Invalid tenant");
            }

            try {
                tenantUseCase.deleteTenant(TenantRef.valueOf(tenant));
            } catch (UnauthorizedException e) {
                throw new NotFoundException(tenant);
            }

            return Response.noContent().build();
        });
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelEntry {
        private String name;
        private String description;
        private String version;
        private boolean active;
    }

    public static abstract class ListOfModelEntry extends ItemList<ModelEntry> {
    }
}
