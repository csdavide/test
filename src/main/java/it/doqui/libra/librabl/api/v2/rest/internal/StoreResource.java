package it.doqui.libra.librabl.api.v2.rest.internal;

import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ContentStoreService;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Path("/files")
@Slf4j
public class StoreResource {

    @ConfigProperty(name = "libra.files.shared-sec")
    Optional<String> sharedSec;

    @Inject
    ContentStoreService contentStoreManager;

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(hidden = true)
    public Response getFileData(@QueryParam("contentUrl") String contentUrl, @Context HttpHeaders headers) throws IOException {
        verifySharedSecret(headers);

        var cxt = UserContextManager.getContext();
        if (cxt != null) {
            log.info("User connected as {}", cxt.getAuthorityRef());
        }

        var file = contentStoreManager.getPath(contentUrl).toFile();
        return Response
            .ok(file)
            .header("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
            .build();
    }

    @PUT
    @Path("/{contentUrl}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(hidden = true)
    public Response writeFileData(@PathParam("contentUrl") String contentUrl, @Context HttpHeaders headers, @RequestBody InputStream is) throws IOException {
        verifySharedSecret(headers);

        log.info("Storing file to {} (user {})", contentUrl, Optional.ofNullable(UserContextManager.getContext()).map(UserContext::getAuthorityRef).map(Object::toString).orElse("anonymous"));
        long size = contentStoreManager.writeStream(contentUrl, is);
        return Response.ok(new ImmutablePair<>("size", size)).build();
    }

    @DELETE
    @Path("/{contentUrl}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Operation(hidden = true)
    public Response writeFileData(@PathParam("contentUrl") String contentUrl, @Context HttpHeaders headers) throws IOException {
        verifySharedSecret(headers);

        log.info("Deleting url {} (user {})", contentUrl, Optional.ofNullable(UserContextManager.getContext()).map(UserContext::getAuthorityRef).map(Object::toString).orElse("anonymous"));
        try {
            contentStoreManager.delete(contentUrl);
            return Response.noContent().build();
        } catch (FileNotFoundException e) {
            return Response.status(404).build();
        } catch (IOException e) {
            return Response.status(403).build();
        }
    }

    private void verifySharedSecret(HttpHeaders headers) {
        if (sharedSec.isPresent() &&!StringUtils.equals(headers.getHeaderString("X-Shared-Sec"), sharedSec.get())) {
            throw new UnauthorizedException();
        }
    }
}
