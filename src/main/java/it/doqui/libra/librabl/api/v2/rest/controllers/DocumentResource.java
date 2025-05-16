package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.interfaces.DocumentService;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.document.DocumentSignOperationResponse;
import it.doqui.libra.librabl.views.document.ErrorReport;
import it.doqui.libra.librabl.views.document.SignOperation;
import it.doqui.libra.librabl.views.document.VerifyRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.time.Duration;
import java.util.Optional;

@Path("/v2/documents")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class DocumentResource extends AbstractResource {

    @Inject
    DocumentService documentService;


    @POST
    @Path("/verifications")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "verifyDocument", summary = "Performs a document verification, possibly with a detached document, both represented by a uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Document is verified correctly", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "412", description = "Uuids not set as input parameters"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response verifyDocument(
        @RequestBody(
            description = "Verification params",
            required = true,
            content = @Content(schema = @Schema(implementation = VerifyRequest.class))
        ) @TraceParam(ignore = true) VerifyRequest verifyRequest
    ) {
        return call(() -> {
            if (verifyRequest.getDocument().getUuid() == null) {
                throw new PreconditionFailedException("Document uuid must be set.");
            }

            var response = documentService.verifyDocument(
                verifyRequest.getDocument(),
                verifyRequest.getDetachedDocument(),
                verifyRequest.getVerificationDateTime(),
                Optional.ofNullable(verifyRequest.getTimeout()).map(Duration::ofMillis).orElse(null),
                verifyRequest.getMode());
            return Response.ok(response).build();
        });
    }

    @GET
    @Path("/verifications/{requestId}")
    @Operation(operationId = "getVerificationReport", summary = "Get a report about a previous submitted verification request")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A verification report is returned", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden operation"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getVerificationReport(@PathParam("requestId") String requestId) {
        return call(() -> {
            var response = documentService.getVerificationReport(requestId);
            return Response.ok(response).build();
        });
    }

    @GET
    @Path("/sealings/{requestId}")
    @Operation(operationId = "getAsyncSigillo", summary = "Get a report about a previous submitted sealing request")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A sealing report is returned", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getSealReport(@PathParam("requestId") String requestId) {
        return call(() -> Response.ok(documentService.getSealingReport(requestId)).build());
    }

    @GET
    @Path("/errors/{requestId}")
    @Operation(operationId = "getErrorReport", summary = "Get a report about errors obtained previously in a verification or sealing request")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "An error report is obtained correctly", content = @Content(schema = @Schema(implementation = ErrorReport.class))),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getErrorReport(
        @PathParam("requestId") String requestId,
        @Parameter(description = "Type of document operation", schema = @Schema(implementation = String.class, enumeration = {"VERIFY", "SEAL"}), required = true)
        @QueryParam("type") String type
        ) {
        SignOperation documentOperation = validateAndGet(() -> SignOperation.valueOf(type));
        return call(() -> Response.ok(documentService.getErrorReport(requestId, documentOperation)).build());
    }
}
