package it.doqui.libra.librabl.api.v2.rest.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.ReindexService;
import it.doqui.libra.librabl.business.service.interfaces.TenantService;
import it.doqui.libra.librabl.foundation.ItemList;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.views.tenant.TenantItem;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
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

import java.time.ZonedDateTime;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/tenants")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class TenantResource extends AbstractResource {

    @Inject
    TenantService tenantService;

    @Inject
    ReindexService reindexService;

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
        return call(Response.ok(tenantService.findStartingWith(prefix))::build);
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
    public Response getTenant(@PathParam("tenant") String tenant) {
        return call(() -> tenantService
            .findByIdOptional(tenant)
            .map(t -> Response.ok(t).build())
            .orElseThrow(NotFoundException::new));
    }

    @POST
    @Operation(operationId = "createTenant", summary = "Create or complete creation of a tenant")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The specified tenant has been created", content = @Content(schema = @Schema(implementation = TenantSpace.class))),
        @APIResponse(responseCode = "304", description = "The tenant is already configured"),
        @APIResponse(responseCode = "400", description = "Invalid tenant name"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed(UserContext.ROLE_SYSADMIN)
    public Response createTenant(@RequestBody TenantCreationRequest t) {
        return call(() -> {
            if (StringUtils.isBlank(t.getTenant())) {
                throw new BadRequestException("No tenant name specified");
            }

            TenantSpace createdTenant = tenantService.createTenant(t);
            if (createdTenant != null) {
                return Response.ok(createdTenant).build();
            } else {
                return Response.notModified().build();
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
                tenantService.syncTenant(TenantRef.valueOf(tenant), includeAny);
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
            if (!StringUtils.equalsIgnoreCase(tenant, confirmationTenant)) {
                throw new BadRequestException("Invalid tenant");
            }

            try {
                tenantService.deleteTenant(TenantRef.valueOf(tenant));
            } catch (UnauthorizedException e) {
                throw new NotFoundException(tenant);
            }

            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{tenant}/transactions/{tx}/indexing")
    @Operation(operationId = "reindexTransaction", summary = "Submit a reindex operation of the specified transaction")
    @Traceable(traceAllParameters = true, category = TraceCategory.MANAGEMENT)
    @APIResponses(value = {
        @APIResponse(responseCode = "202", description = "The specified transaction will be re-indexed"),
        @APIResponse(responseCode = "403", description = "You are not authorized to submit re-index on the specified tenant"),
        @APIResponse(responseCode = "404", description = "Either tenant nor transaction have not been found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RolesAllowed({UserContext.ROLE_SYSADMIN, UserContext.ROLE_ADMIN})
    public Response reindex(
        @PathParam("tenant") String tenant,
        @PathParam("tx") long txId,
        @QueryParam("flags") @DefaultValue("1111") String flags) {
        return call(() -> {
            reindexService.reindex(TenantRef.valueOf(tenant), txId, flags);
            return Response.accepted().build();
        });
    }

//    @POST
//    @Path("/{tenant}/transactions/indexing")
//    @Operation(operationId = "reindexTransactions", summary = "Submit a reindex operation for a range of transactions in the specified tenant")
//    @APIResponses(value = {
//        @APIResponse(responseCode = "202", description = "The specified transactions will be re-indexed", content = @Content(schema = @Schema(implementation = Identifier.class))),
//        @APIResponse(responseCode = "403", description = "You are not authorized to submit re-index on the specified tenant"),
//        @APIResponse(responseCode = "404", description = "The tenant has not been found"),
//        @APIResponse(responseCode = "500", description = "Unexpected error")
//    })
//    @RolesAllowed(UserContext.ROLE_SYSADMIN)
//    public Response reindex(@PathParam("tenant") String tenant, @RequestBody ReindexRequestDTO body) {
//        return call(() -> {
//            log.debug("Got reindex request {}", body);
//            String uuid = reindexService.reindex(TenantRef.valueOf(tenant), body.getFromDateTime(), body.getToDateTime(),
//                body.getFlags(), body.getBlockSize(), body.isAddOnly());
//            return Response.accepted().entity(new ImmutablePair<>("uuid", uuid)).build();
//        });
//    }

    @Getter
    @Setter
    @ToString
    public static class ReindexRequestDTO {
        @JsonProperty("from")
        private ZonedDateTime fromDateTime;

        @JsonProperty("to")
        private ZonedDateTime toDateTime;

        private String flags;
        private int blockSize = 1;
        private boolean addOnly = false;
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
