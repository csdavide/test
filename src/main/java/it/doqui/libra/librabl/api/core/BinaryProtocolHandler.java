package it.doqui.libra.librabl.api.core;

import it.doqui.index.ecmengine.mtom.dto.MtomOperationContext;
import it.doqui.libra.bridge.AuthContext;
import it.doqui.libra.bridge.LibraRequest;
import it.doqui.libra.librabl.api.v1.cxf.impl.ServiceDispatcher;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@Path("/v1/bin/requests")
@Slf4j
public class BinaryProtocolHandler {

    @Inject
    ServiceDispatcher serviceDispatcher;

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(hidden = true)
    public Response handleRequest(byte[] payload) {
        try (var is = new ByteArrayInputStream(payload); var ois = new ObjectInputStream(is)) {
            var request = (LibraRequest) ois.readObject();
            Object result;
            try {
                result = serviceDispatcher.invoke(request.getMethod(), request.getParams().stream().map(this::map).toArray(Object[]::new));
            } catch (Exception e) {
                result = e;
            }

            try (var bos = new ByteArrayOutputStream(); var os = new ObjectOutputStream(bos)) {
                os.writeObject(result);
                return Response.ok(bos.toByteArray()).build();
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    private Object map(Object in) {
        if (in instanceof AuthContext a) {
            MtomOperationContext ctx = new MtomOperationContext();
            ctx.setNomeFisico(a.getNomeFisico());
            ctx.setFruitore(a.getFruitore());
            ctx.setRepository(a.getRepository());
            ctx.setUsername(a.getUsername());
            ctx.setPassword(a.getPassword());
            return ctx;
        }

        return in;
    }
}
