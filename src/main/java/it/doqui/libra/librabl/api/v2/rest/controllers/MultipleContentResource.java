package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.v2.rest.dto.document.DocumentVerificationParameters;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.document.CertificateParams;
import it.doqui.libra.librabl.views.document.DocumentSignOperationResponse;
import it.doqui.libra.librabl.views.document.FileFormatDescriptor;
import it.doqui.libra.librabl.views.document.SealRequest;
import it.doqui.libra.librabl.views.node.ContentRef;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

@Path("/v2/nodes/{uuid}/contents")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class MultipleContentResource extends AbstractContentResource {

    @PUT
    @Path("/{contentPropertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "updateNodeContent", summary = "Create or replace the node attachment with a specified property name")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node content attachment has been replaced"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "Unable to find the content to replace"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updateNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("currentFileName") String currentFilename,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @HeaderParam("Content-Disposition") String contentDisposition,
        @RequestBody @TraceParam(ignore = true) InputStream body
    ) {
        return call(() -> {
            nodeContentService.setNodeContent(uuid, makeContentStream(contentPropertyName, Optional.ofNullable(StringUtils.stripToNull(customContentType)).orElse(contentType), contentDisposition, body), currentFilename);
            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{contentPropertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "addNodeContent", summary = "Add node attachment with a specified property name")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node content attachment has been added"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response addNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @HeaderParam("Content-Disposition") String contentDisposition,
        @RequestBody InputStream body
    ) {
        return call(() -> {
            nodeContentService.addNodeContent(uuid, makeContentStream(contentPropertyName, Optional.ofNullable(StringUtils.stripToNull(customContentType)).orElse(contentType), contentDisposition, body));
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/{contentPropertyName}")
    @Produces(MediaType.WILDCARD)
    @Operation(operationId = "retrieveNodeContent", summary = "Download a node attachment")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The node file attachment has been downloaded"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node has no file attachment"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response retrieveNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName,
        @Parameter(description = "Request for inline content disposition")
        @QueryParam("inline") @DefaultValue("false") boolean inline) {
        try {
            return getNodeContent(uuid, contentPropertyName, inline, fileName);
        } catch (PreconditionFailedException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @DELETE
    @Path("/{contentPropertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removeNodeContent", summary = "Remove a node attachment")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node file attachment has been removed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node has no file attachment"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response removeNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName) {
        return call(() -> {
            nodeContentService.removeNodeContent(uuid, contentPropertyName, fileName);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/{contentPropertyName}/digest")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "computeDigestOfNodeContent", summary = "Compute the digest of the related document")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "The content file digest is returned",
            content = @Content(schema = @Schema(implementation = FileFormatDescriptor.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response computeDigestOfNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName,
        @QueryParam("enveloped") @DefaultValue("false") boolean enveloped,
        @Parameter(description = "Describes the algorithm that will be used", required = true, example = "MD5")
        @QueryParam("alg") @DefaultValue("MD5") String alg) {
        return digest(uuid, contentPropertyName, fileName, enveloped, alg);
    }

    @GET
    @Path("/{contentPropertyName}/format")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getNodeContentFormat", summary = "Analyze the related document and return the file format")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "The content file format descriptor is returned",
            content = @Content(schema = @Schema(implementation = FileFormatDescriptor.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response getNodeContentFormat(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName) {
        return getContentFormat(uuid, contentPropertyName, fileName);
    }

    @POST
    @Path("/{contentPropertyName}/extractions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "unwrapNodeContent", summary = "Extract the related content into a new temporary node")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "The uuid of the new created node is returned",
            content = @Content(schema = @Schema(implementation = ContentRef.class))
        ),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response unwrapNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName) {
        return unwrap(uuid, contentPropertyName, fileName);
    }

    @POST
    @Path("/{contentPropertyName}/verifications")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "verifyNodeContent", summary = "Performs a document verification, possibly with a detached document, both represented by a uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Document is verified correctly", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response verifyNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName,
        @RequestBody(
            description = "Verification params",
            required = true,
            content = @Content(schema = @Schema(implementation = DocumentVerificationParameters.class))
        ) @TraceParam(ignore = true) DocumentVerificationParameters verifyRequest
    ) {
        return call(() -> {
            var response = documentService.verifyDocument(
                contentRef(uuid, contentPropertyName, fileName),
                null,
                verifyRequest.getVerificationDateTime(),
                Optional.ofNullable(verifyRequest.getTimeout()).map(Duration::ofMillis).orElse(null),
                verifyRequest.getMode());
            return Response.ok(response).build();
        });
    }

    @POST
    @Path("/{contentPropertyName}/sealings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "sealNodeContent", summary = "Signs a node content")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A sealing response is returned", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response sealNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName,
        @RequestBody(
            description = "All params requested for sealing",
            required = true,
            content = @Content(schema = @Schema(implementation = SealRequest.class))
        ) @TraceParam(ignore = true) SealRequest sealRequest
    ) {
        return call(() -> Response.ok(
            documentService.sealDocument(
                contentRef(uuid, contentPropertyName, fileName),
                sealRequest.getSealParams(),
                Optional.ofNullable(sealRequest.getTimeout()).map(Duration::ofMillis).orElse(null),
                sealRequest.getMode()
            )
        ).build());
    }

    @POST
    @Path("/{contentPropertyName}/certificates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "verifyNodeCertificate", summary = "Performs a certificate verification on a specified content of a node")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A certificate report is returned", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response verifyCertificateNodeContent(
        @PathParam("uuid") String uuid,
        @PathParam("contentPropertyName") String contentPropertyName,
        @QueryParam("fileName") String fileName,
        @RequestBody(
            description = "All params requested for certificate verification",
            required = true,
            content = @Content(schema = @Schema(implementation = CertificateParams.class))
        ) @TraceParam(ignore = true) CertificateParams params
    ) {
        return verifyCertificate(uuid, contentPropertyName, fileName, params);
    }
}
