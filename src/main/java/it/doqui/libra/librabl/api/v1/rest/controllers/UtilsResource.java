package it.doqui.libra.librabl.api.v1.rest.controllers;

import it.doqui.libra.librabl.api.v1.rest.components.interfaces.UtilsBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.api.v1.rest.dto.SigilloSigner;
import it.doqui.libra.librabl.api.v1.rest.dto.VerifyParameter;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

@Path("/v1/utils")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class UtilsResource extends AbstractBridgeResource {

    @GET
    @Path("/_identify/mimetype")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getMimeType(@QueryParam("fileExtension") String fileExtension, @QueryParam("mimetype") String mimetype) {
        return call(() -> {
            var ext = fileExtension == null ? null : fileExtension.substring(fileExtension.lastIndexOf('.') + 1);
            return Response.ok(dispatcher.getProxy(UtilsBusinessInterface.class).getMimetype(ext, mimetype)).build();
        });
    }

    @GET
    @Path("/jobs/{jobId}/async/report")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getAsyncReport(@PathParam("jobId") String jobId) {
        return call(() -> Response.ok(dispatcher.getProxy(UtilsBusinessInterface.class).getAsyncReport(jobId)).build());
    }

    @GET
    @Path("/jobs/{jobId}/async/sigillo")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getAsyncSigillo(@PathParam("jobId") String jobId) {
        return call(() -> Response.ok(dispatcher.getProxy(UtilsBusinessInterface.class).getAsyncSigillo(jobId)).build());
    }

    @GET
    @Path("/jobs/{jobId}")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getServiceJobInfo(@PathParam("jobId") String jobId) {
        return call(() -> Response.ok(dispatcher.getProxy(UtilsBusinessInterface.class).getServiceJobInfo(jobId)).build());
    }

    @POST
    @Path("/_identify/document")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response identifyDocumentFile(@TraceParam(ignore = true) MultipartFormDataInput input, @QueryParam("store") Boolean store) {
        return call(() -> {
            var bytes = getByteArraysFromMultipart("binaryContent", input, true);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class).identifyDocument(bytes, store);
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/_identify/signature")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getSignatureTypeFile(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            var bytes = getByteArraysFromMultipart("binaryContent", input, true);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class).getSignatureType(bytes);
            return Response.ok(new Data(result)).build();
        });
    }

    @POST
    @Path("/_verify/certificate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response verifyCertificate(@TraceParam(ignore = true) MultipartFormDataInput input, @QueryParam("store") Boolean store) {
        return call(() -> {
            var vp = getObjectFromMultipart("verifyParameter", input, false, VerifyParameter.class);
            var bytes = getByteArraysFromMultipart("binaryContent", input, true);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class).verifyCertificate(vp, bytes, store);
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/_extractFromEnvelope")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Response extractDocumentFromEnvelopeFile(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            var bytes = getByteArraysFromMultipart("binaryContent", input, true);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class).extractFromEnvelope(bytes);
            return Response.ok(new Data(result)).build();
        });
    }

    @POST
    @Path("/digest")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response generateDigestFromContent(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            var algorithm = getStringFromMultipart("algorithm", input, true);
            var bytes = getByteArraysFromMultipart("binaryContent", input, true);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class).generateDigestFromContent(bytes, algorithm);
            return Response.ok(new Data(result)).build();
        });
    }

    @POST
    @Path("/_verify/document/_async")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response verifyAsyncDocument(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            byte[] documentBinaryContent = getByteArraysFromMultipart("documentBinaryContent", input, false);
            String documentUid = getStringFromMultipart("documentUid", input, false);
            String documentContentPropertyName = getStringFromMultipart("documentContentPropertyName", input, false);
            String documentStore = getStringFromMultipart("documentStore", input, false);
            byte[] detachedSignatureBinaryContent = getByteArraysFromMultipart("detachedSignatureBinaryContent", input,
                false);
            String detachedSignatureUid = getStringFromMultipart("detachedSignatureUid", input, false);
            String detachedSignatureContentPropertyName = getStringFromMultipart("detachedSignatureContentPropertyName",
                input, false);
            String detachedSignatureStore = getStringFromMultipart("detachedSignatureStore", input, false);
            var vp = getObjectFromMultipart("verifyParameter", input, false, VerifyParameter.class);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class)
                .verifyAsyncDocument(documentBinaryContent, documentUid,
                    documentContentPropertyName, documentStore, detachedSignatureBinaryContent,
                    detachedSignatureUid, detachedSignatureContentPropertyName,
                    detachedSignatureStore, vp);
            return Response.ok(new Data(result)).build();
        });
    }

    @POST
    @Path("/_verify/document/ext")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response verifyDocumentExt(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            byte[] documentBinaryContent = getByteArraysFromMultipart("documentBinaryContent", input, false);
            String documentUid = getStringFromMultipart("documentUid", input, false);
            String documentContentPropertyName = getStringFromMultipart("documentContentPropertyName", input, false);
            String documentStore = getStringFromMultipart("documentStore", input, false);
            byte[] detachedSignatureBinaryContent = getByteArraysFromMultipart("detachedSignatureBinaryContent", input,
                false);
            String detachedSignatureUid = getStringFromMultipart("detachedSignatureUid", input, false);
            String detachedSignatureContentPropertyName = getStringFromMultipart("detachedSignatureContentPropertyName",
                input, false);
            String detachedSignatureStore = getStringFromMultipart("detachedSignatureStore", input, false);
            var vp = getObjectFromMultipart("verifyParameter", input, false, VerifyParameter.class);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class)
                .verifyDocumentExt(documentBinaryContent,
                    documentUid, documentContentPropertyName, documentStore, detachedSignatureBinaryContent,
                    detachedSignatureUid, detachedSignatureContentPropertyName, detachedSignatureStore,
                    vp);
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/_verify/document/signed")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response verifySignedDocument(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            byte[] documentBinaryContent = getByteArraysFromMultipart("documentBinaryContent", input, false);
            String documentUid = getStringFromMultipart("documentUid", input, false);
            String documentContentPropertyName = getStringFromMultipart("documentContentPropertyName", input, false);
            String documentStore = getStringFromMultipart("documentStore", input, false);
            byte[] detachedSignatureBinaryContent = getByteArraysFromMultipart("detachedSignatureBinaryContent", input,
                false);
            String detachedSignatureUid = getStringFromMultipart("detachedSignatureUid", input, false);
            String detachedSignatureContentPropertyName = getStringFromMultipart("detachedSignatureContentPropertyName",
                input, false);
            String detachedSignatureStore = getStringFromMultipart("detachedSignatureStore", input, false);
            var vp = getObjectFromMultipart("verifyParameter", input, false, VerifyParameter.class);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class)
                .verifySignedDocument(documentBinaryContent,
                    documentUid, documentContentPropertyName, documentStore, detachedSignatureBinaryContent,
                    detachedSignatureUid, detachedSignatureContentPropertyName, detachedSignatureStore,
                    vp);
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/_sign/document/sigillo/ext")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response sigilloSignatureExt(@TraceParam(ignore = true) MultipartFormDataInput input) {
        return call(() -> {
            byte[] documentBinaryContent = getByteArraysFromMultipart("documentBinaryContent", input, false);
            String documentUid = getStringFromMultipart("documentUid", input, false);
            String documentContentPropertyName = getStringFromMultipart("documentContentPropertyName", input, false);
            var signer = getObjectFromMultipart("sigilloSigner", input, false, SigilloSigner.class);
            var result = dispatcher.getProxy(UtilsBusinessInterface.class)
                .sigilloSignatureExt(documentBinaryContent, documentUid, documentContentPropertyName, signer);
            return Response.ok(result).build();
        });
    }
}
