package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing.IndexerDelegate;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.platform.tx.TxReindexRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/management/reindexes")
@Slf4j
@RolesAllowed({UserContext.ROLE_SYSADMIN, UserContext.ROLE_POWERADMIN})
public class ReindexResource extends AbstractResource {

    @Inject
    IndexerDelegate indexerDelegate;

    @POST
    @Operation(operationId = "submitReindex", summary = "Submit a reindex")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Reindex submitted"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response submitReindex(TxReindexRequest txReindexRequest) {
        return call(() -> {
            indexerDelegate.submitReindex(txReindexRequest);
            return Response.accepted().build();
        });
    }

    @POST
    @Path("/csv")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(operationId = "reindexTransactionsInCSV", summary = "Submit the reindex of a list of transactions provided in a csv file")
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Reindex submitted"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Unexpected error")
    })
    public Response reindexTransactionsInCSV(ReindexCSVUploadRequest request) {
        if (request.getFile() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("CSV File is missing").build();
        }

        return call(() -> {
            final int blockSize = request.getBlockSize();
            try (var reader = new BufferedReader(new InputStreamReader(request.getFile().uploadedFile().toFile().toURI().toURL().openStream()))) {
                String line;
                long total = 0;
                final var txList = new ArrayList<Long>(blockSize);
                final var tenant = request.getTenant();
                final var priority = request.getPriority();
                while ((line = reader.readLine()) != null) {
                    var fields = line.split(request.getDelimiter());
                    if (fields.length > 0 && StringUtils.isNotBlank(fields[0])) {
                        try {
                            var tx = Long.parseLong(fields[0]);
                            txList.add(tx);
                            if (txList.size() >= blockSize) {
                                total += reindex(tenant, txList, priority);
                            }
                        } catch (Exception e) {
                            log.error("Error processing tx '{}'", fields[0], e);
                        }
                    }
                } // end while

                if (!txList.isEmpty()) {
                    total += reindex(tenant, txList, priority);
                }

                log.info("{} transactions submitted", total);
            } catch (Exception e) {
                throw new SystemException(e);
            }

            return Response.accepted().build();
        });
    }

    private int reindex(final String tenant, final List<Long> txList, final int priority) {
        int n = txList.size();
        var txReindexRequest = new TxReindexRequest();
        txReindexRequest.setTenant(tenant);
        txReindexRequest.getTransactions().addAll(txList);
        txReindexRequest.setPriority(priority);
        indexerDelegate.submitReindex(txReindexRequest);
        Long last = txList.isEmpty() ? null : txList.get(txList.size() - 1);
        log.debug("{} transactions sent to reindex (last TX: {})", n, last);
        txList.clear();
        return n;
    }

    @Getter
    @Setter
    @ToString(exclude = "file")
    public static class ReindexCSVUploadRequest {

        @RestForm
        private FileUpload file;

        @RestForm
        @DefaultValue(";")
        private String delimiter;

        @RestForm
        @DefaultValue("false")
        private boolean hasHeader;

        @RestForm
        private String tenant;

        @RestForm
        @DefaultValue("0")
        private int priority;

        @RestForm
        @DefaultValue("40")
        private int blockSize;
    }
}
