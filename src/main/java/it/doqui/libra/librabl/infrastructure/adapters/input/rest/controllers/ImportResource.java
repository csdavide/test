package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.ImportUseCase;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.ingest.ImportStatement;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Encoding;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.time.Duration;

@Path("/v2/imports")
@Slf4j
@RolesAllowed(UserContext.ROLE_ADMIN)
public class ImportResource extends AbstractResource {

    @Inject
    ImportUseCase importUseCase;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(operationId = "importDataSet", summary = "Import a set of nodes from a dataset")
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "202",
                    description = "The import request has been submitted",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))
            ),
            @APIResponse(responseCode = "403", description = "Permission denied"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    @RequestBody(content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA,
            schema = @Schema(implementation = ImportPayloadRequest.class),
            encoding = {
                    @Encoding(name = "file", contentType = "*/*")
            }
    ))
    public Response importDataSet(ImportPayloadRequest payloadRequest) {
        return call(() -> {
            var result = importUseCase.submitImportDataSet(
                    Files.newInputStream(payloadRequest.getFile().uploadedFile()),
                    payloadRequest.getFile().contentType(),
                    payloadRequest.getDuration(),
                    payloadRequest.getStatement()
            );
            return Response.ok(result).build();
        });
    }

    @Getter
    @Setter
    @ToString(exclude = "file")
    public static class ImportPayloadRequest {
        @RestForm
        @PartType(MediaType.WILDCARD)
        private FileUpload file;

        @RestForm
        @DefaultValue("PT24H")
        private Duration duration;

        @RestForm("import")
        @Schema(name = "import")
        @PartType(MediaType.APPLICATION_JSON)
        private ImportStatement statement;
    }
}
