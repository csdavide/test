package it.doqui.libra.librabl.api.v1.rest.controllers;


import it.doqui.libra.librabl.api.v1.rest.dto.Data;
import it.doqui.libra.librabl.business.service.interfaces.ManagementService;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/v1/tests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class TestResource extends AbstractBridgeResource {

    @Inject
    ManagementService managementService;

    @GET
    @Path("/version")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response getServiceVersion() {
        var map = managementService.getBootAttributes();
        var version = map.get("git.build.version");
        var time = map.get("git.build.time");
        var commitIdAbbrev = map.get("git.commit.id.abbrev");
        var msg = String.format("Product %s%s built at %s", version, StringUtils.isBlank(commitIdAbbrev.toString()) ? "" : " " + commitIdAbbrev, time.toString());
        return Response.ok(new Data(msg)).build();
    }

    @GET
    @Path("/ping")
    @Operation(hidden = true)
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Response testsPingGet() {
        return Response.ok(new Data("Ok")).build();
    }
}
