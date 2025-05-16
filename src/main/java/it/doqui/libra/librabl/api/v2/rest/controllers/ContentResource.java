package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.v2.rest.dto.document.DocumentVerificationParameters;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
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

import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_CONTENT;

@Path("/v2/nodes/{uuid}/content")
@Slf4j
@RolesAllowed(UserContext.ROLE_USER)
public class ContentResource extends AbstractContentResource {

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(
        operationId = "updateDefaultNodeContent",
        summary = "Create or replace the default node attachment, having property cm:content")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node content attachment has been replaced"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "Unable to find the content to replace"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response updateDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("currentFilename") String currentFilename,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @HeaderParam("Content-Disposition") String contentDisposition,
        @RequestBody InputStream body
    ) {
        return call(() -> {
            nodeContentService.setNodeContent(uuid, makeContentStream(CM_CONTENT, Optional.ofNullable(StringUtils.stripToNull(customContentType)).orElse(contentType), contentDisposition, body), currentFilename);
            return Response.noContent().build();
        });
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "addDefaultNodeContent", summary = "Add node attachment with property name cm:content")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node content attachment has been added"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response addDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("X-Content-Type") String customContentType,
        @HeaderParam("Content-Disposition") String contentDisposition,
        @RequestBody InputStream body
    ) {
        return call(() -> {
            nodeContentService.addNodeContent(uuid, makeContentStream(CM_CONTENT, Optional.ofNullable(StringUtils.stripToNull(customContentType)).orElse(contentType), contentDisposition, body));
            return Response.noContent().build();
        });
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removeDefaultNodeContent", summary = "Remove a node attachment from property cm:content. A filename can be specified in case of multiple content")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", description = "The node file attachment has been removed"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node has no file attachment"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response removeDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName) {
        return call(() -> {
            nodeContentService.removeNodeContent(uuid, CM_CONTENT, fileName);
            return Response.noContent().build();
        });
    }

    @GET
    @Produces(MediaType.WILDCARD)
    @Operation(operationId = "retrieveDefaultNodeContent", summary = "Download node attachment having property cm:content. A filename can be specified in case of multiple content")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "The node file attachment has been downloaded"),
        @APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node has no file attachment"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response retrieveDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName,
        @Parameter(description = "Request for inline content disposition")
        @QueryParam("inline") @DefaultValue("false") boolean inline) {
        return getNodeContent(uuid, CM_CONTENT, inline, fileName);
    }

    @GET
    @Path("/digest")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "computeDigestOfDefaultNodeContent", summary = "Compute the digest of the related document")
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
    public Response computeDigestOfDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName,
        @QueryParam("enveloped") @DefaultValue("false") boolean enveloped,
        @Parameter(description = "Describes the algorithm that will be used", required = true, example = "MD5")
        @QueryParam("alg") @DefaultValue("MD5") String alg) {
        return digest(uuid, CM_CONTENT, fileName, enveloped, alg);
    }

    @GET
    @Path("/format")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getDefaultNodeContentFormat", summary = "Analyze the related document and return the file format")
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
    public Response getDefaultNodeContentFormat(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName) {
        return getContentFormat(uuid, CM_CONTENT, fileName);
    }

    @POST
    @Path("/extractions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "unwrapDefaultNodeContent", summary = "Extract the related content into a new temporary node")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "The uuid of the new created node is returned",
            content = @Content(schema = @Schema(implementation = ContentRef.class))
        ),@APIResponse(responseCode = "403", description = "Permission denied"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response unwrapDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName) {
        return unwrap(uuid, CM_CONTENT, fileName);
    }

    @POST
    @Path("/verifications")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "verifyDefaultNodeContent", summary = "Performs a document verification, possibly with a detached document, both represented by a uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Document is verified correctly", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "412", description = "Uuids not set as input parameters"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response verifyDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName,
        @RequestBody(
            description = "Verification params",
            required = true,
            content = @Content(schema = @Schema(implementation = DocumentVerificationParameters.class))
        ) DocumentVerificationParameters verifyRequest
    ) {
        return call(() -> {
            var response = documentService.verifyDocument(
                contentRef(uuid, CM_CONTENT, fileName),
                null,
                verifyRequest.getVerificationDateTime(),
                Optional.ofNullable(verifyRequest.getTimeout()).map(Duration::ofMillis).orElse(null),
                verifyRequest.getMode());
            return Response.ok(response).build();
        });
    }

    @POST
    @Path("/sealings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "sealDefaultNodeContent", summary = "Signs a node content")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A sealing response is returned", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response sealDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName,
        @RequestBody(
            description = "All params requested for sealing",
            required = true,
            content = @Content(schema = @Schema(implementation = SealRequest.class))
        ) SealRequest sealRequest
    ) {
        return call(() -> Response.ok(
            documentService.sealDocument(
                contentRef(uuid, CM_CONTENT, fileName),
                sealRequest.getSealParams(),
                Optional.ofNullable(sealRequest.getTimeout()).map(Duration::ofMillis).orElse(null),
                sealRequest.getMode()
            )
        ).build());
    }

    @POST
    @Path("/certificates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "verifyCertificateDefaultNodeContent", summary = "Performs a certificate verification, represented by a uuid")
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "A verify-certificate report is returned", content = @Content(schema = @Schema(implementation = DocumentSignOperationResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "The specified node is not found"),
        @APIResponse(responseCode = "412", description = "The specified node content is not found"),
        @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response verifyCertificateDefaultNodeContent(
        @PathParam("uuid") String uuid,
        @QueryParam("fileName") String fileName,
        @RequestBody(
            description = "All params requested for verifying certificate",
            required = true,
            content = @Content(schema = @Schema(implementation = CertificateParams.class))
        ) CertificateParams params
    ) {
        return verifyCertificate(uuid, CM_CONTENT, fileName, params);
    }
}
