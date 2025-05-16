package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.api.v1.rest.dto.EncryptionInfo;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

@Path("/v1/tenants/{tenantName}/nodes/{uid}/contents")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class ContentResource extends AbstractBridgeResource {

    @GET
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response retrieveContentData(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("contentPropertyName") String contentPropertyName) {
        return call(tenant, () -> {
            var file = dispatcher.getProxy(NodesBusinessInterface.class).retrieveContentData(uuid, contentPropertyName);
            return Response.ok(file)
                .type(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + uuid + ".bin\"")
                .header("Content-Length", file.length())
                .build();
        });
    }

    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Response updateContentData(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("contentPropertyName") String contentPropertyName,
        @TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(tenant, () -> {
            var mimeType = getStringFromMultipart("mimeType", input, true);
            var encoding = getStringFromMultipart("encoding", input, true);
            var bytes = getByteArraysFromMultipart("binaryContent", input, true);
            dispatcher.getProxy(NodesBusinessInterface.class)
                .updateContentData(uuid, contentPropertyName, mimeType, encoding, bytes);
            return Response.ok().build();
        });
    }

    @GET
    @Path("/_extractFromEnvelope")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response extractDocumentFromEnvelope(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("contentPropertyName") String contentPropertyName) {
        return call(tenant, () -> {
            var result = dispatcher.getProxy(NodesBusinessInterface.class).extractFromEnvelope(uuid, contentPropertyName);
            return Response.ok(new Data(result)).build();
        });
    }

    @GET
    @Path("/_identify/document")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response identifyDocument(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("contentPropertyName") String contentPropertyName,
        @QueryParam("store") Boolean store) {
        return call(tenant, () -> {
            var result = dispatcher.getProxy(NodesBusinessInterface.class).identifyDocument(uuid, contentPropertyName, store);
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/_identify/signature")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getSignatureType(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("contentPropertyName") String contentPropertyName,
        EncryptionInfo encryptionInfo) {
        return call(tenant, () -> {
            var result = dispatcher.getProxy(NodesBusinessInterface.class)
                .getSignatureType(uuid, contentPropertyName, encryptionInfo);
            return Response.ok(new Data(result)).build();
        });
    }

    @GET
    @Path("/digest")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response generateDigestFromUID(
        @PathParam("tenantName") String tenant,
        @PathParam("uid") String uuid,
        @QueryParam("contentPropertyName") String contentPropertyName,
        @QueryParam("algorithm") String algorithm,
        @QueryParam("enveloped") Boolean enveloped) {
        return call(tenant, () -> {
            if (algorithm == null) {
                throw new BadRequestException("Null algorithm");
            }
            if (enveloped == null) {
                throw new BadRequestException("Null enveloped");
            }

            var result = dispatcher.getProxy(NodesBusinessInterface.class)
                .generateDigestFromUID(uuid, contentPropertyName, algorithm, enveloped);
            return Response.ok(new Data(result)).build();
        });
    }
}
