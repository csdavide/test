package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.domain.model.document.DocumentOperation;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.DocumentUseCase;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document.SealRequest;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document.SignRequest;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document.VerifyRequest;
import it.doqui.libra.librabl.application.model.document.*;
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
    DocumentUseCase documentService;


    @POST
    @Path("/verifications")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "verifyDocument", summary = "Performs a document verification, possibly with a detached document, both represented by a uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Document is verified correctly", content = @Content(schema = @Schema(implementation = DocumentOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "412", description = "Uuid not set as input parameters or the specified node is not a content"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response verifyDocument(
        @RequestBody(
            description = "Verification params",
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

    @POST
    @Path("/seals")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "performSeal", summary = "Performs a sealing on a document, represented by an uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Document is sealed correctly", content = @Content(schema = @Schema(implementation = DocumentOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response sealDocument(
        @RequestBody(
            description = "Sealing params",
            content = @Content(schema = @Schema(implementation = SealRequest.class))
        ) @TraceParam(ignore = true) SealRequest sealRequest
    ) {
        return call(() -> {
            if (sealRequest.getDocument().getUuid() == null || sealRequest.getDocument().getUuid().isEmpty()) {
                throw new PreconditionFailedException("Document uuid must be set.");
            }

            var response = documentService.sealDocument(
                sealRequest.getDocument(),
                sealRequest.getSealParams(),
                sealRequest.getStoreParams(),
                Optional.ofNullable(sealRequest.getTimeout()).map(Duration::ofMillis).orElse(null),
                sealRequest.getMode()
            );
            return Response.ok(response).build();
        });
    }

    @POST
    @Path("/signs")
    @Operation(operationId = "performSign", summary = "Performs a signature of a document, represented by a uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Document is signed correctly", content = @Content(schema = @Schema(implementation = DocumentOperationResponse.class))),
        @APIResponse(responseCode = "202", description = "Async operation is submitted", content = @Content(schema = @Schema(implementation = AsyncOperation.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found or not set or wrong sign type requested"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response signDocument(
        @RequestBody(
            description = "Signing params",
            content = @Content(schema = @Schema(implementation = SignRequest.class))
        ) @TraceParam(ignore = true) SignRequest signRequest
    ) {
        return call(() -> {
            if (signRequest.getDocument().getUuid() == null || signRequest.getDocument().getUuid().isEmpty()) {
                throw new PreconditionFailedException("Document uuid must be set.");
            }

            if (signRequest.getMode().equals(OperationMode.SYNC)) {
                var response = documentService.signDocument(
                    signRequest.getDocument(),
                    signRequest.getSignParams(),
                    signRequest.getStoreParams()
                );
                return Response.ok(response).build();
            } else {
                var op = documentService.submitSignDocument(signRequest.getDocument(), signRequest.getSignParams(), signRequest.getStoreParams());
                return Response.accepted(op).build();
            }
        });
    }

    @GET
    @Path("/verifications/{requestId}")
    @Operation(operationId = "getVerificationReport", summary = "Get a report about a previous submitted verification request")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A verification report is returned", content = @Content(schema = @Schema(implementation = DocumentOperationResponse.class))),
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
    @Path("/seals/{requestId}")
    @Operation(operationId = "getAsyncSigillo", summary = "Get a report about a previous submitted sealing request")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A sealing report is returned", content = @Content(schema = @Schema(implementation = DocumentOperationResponse.class))),
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
        DocumentOperation documentOperation = validateAndGet(() -> DocumentOperation.valueOf(type));
        return call(() -> Response.ok(documentService.getErrorReport(requestId, documentOperation)).build());
    }

    @POST
    @Path("/otp")
    @Operation(operationId = "pushOtp", summary = "Ask an OTP to a specified provider")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "OTP pushed correctly"),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response pushOtp(
        @RequestBody(
            description = "OTP params",
            content = @Content(schema = @Schema(implementation = OTPRequest.class))
        ) OTPRequest otpRequest
    ) {
        return call(() -> {
            documentService.pushOtp(otpRequest);
            return Response.noContent().build();
        });
    }

}
