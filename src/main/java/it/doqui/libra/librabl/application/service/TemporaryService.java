package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.domain.model.document.DocumentStream;
import it.doqui.libra.librabl.domain.model.graph.Constants;
import it.doqui.libra.librabl.application.ports.in.TemporaryUseCase;
import it.doqui.libra.librabl.domain.model.files.ContentDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;

@ApplicationScoped
@Slf4j
public class TemporaryService implements TemporaryUseCase {

    @ConfigProperty(name = "libra.paths.temp", defaultValue = "/app:company_home/cm:temp/")
    String tempHome;

    @Inject
    NodeManager nodeManager;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Override
    public String getTemporaryTenant() {
        return sessionContext.getTenantData().map(TenantData::getTemp).orElse(null);
    }

    @Override
    public ContentRef createEphemeralNode(ContentDescriptor descriptor, @TraceParam(ignore = true) InputStream body, Duration duration) {
        return createEphemeralNode(descriptor, null, body, duration);
    }

    @Override
    public ContentRef createEphemeralNode(ContentDescriptor descriptor, InputNodeRequest extra, @TraceParam(ignore = true) InputStream body, Duration duration) {
        var contentStream = new ContentStream();
        contentStream.setName(ObjectUtils.coalesce(descriptor.getName(), Constants.CM_CONTENT));
        contentStream.setMimetype(ObjectUtils.coalesce(descriptor.getMimetype(), MediaType.APPLICATION_OCTET_STREAM));
        contentStream.setEncoding(descriptor.getEncoding());
        contentStream.setFileName(descriptor.getFileName());
        contentStream.setLocale(Optional.ofNullable(descriptor.getLocale()).map(Objects::toString).orElse(null));
        contentStream.setInputStream(body);

        var link = new LinkItemRequest();
        link.setPath(tempHome);
        link.setRelationship(RelationshipKind.PARENT);
        link.setTypeName(Constants.CM_CONTAINS);
        link.setHard(true);

        var input = new LinkedInputNodeRequest();
        if (extra != null) {
            input.getAspects().addAll(extra.getAspects());
            input.getAspectOperations().putAll(extra.getAspectOperations());
            input.getProperties().putAll(extra.getProperties());
            input.setUnmanagedSgID(extra.getUnmanagedSgID());
        }

        input.getAssociations().add(link);
        input.setTypeName(Optional.ofNullable(extra).map(InputNodeRequest::getTypeName).orElse(Constants.CM_CONTENT));
        input.getAspects().add(Constants.ASPECT_ECMSYS_DISABLED_FULLTEXT);
        input.getAspects().add(Constants.ASPECT_ECMSYS_EPHEMERAL);
        input.getAspects().add(Constants.ASPECT_ECMSYS_EXPIRABLE);
        input.getProperties().put(contentStream.getName(), contentStream);
        input.getProperties().put(Constants.PROP_ECMSYS_EXPIRES_AT,
            ZonedDateTime.now()
                .plus(Optional.ofNullable(duration).orElse(Duration.ofDays(1)))
                .toString());

        return transactionManagerPort.doOnTemp(() -> {
            var contentRef = new ContentRef();
            contentRef.setTenant(sessionContext.getTenant());
            contentRef.setIdentity(sessionContext.getUserContext().getAuthorityRef().getIdentity());
            contentRef.setContentPropertyName(contentStream.getName());
            contentRef.setUuid(nodeManager.createNode(input, Set.of(), (tx,node) -> {
                var cp = node.getData().getFileData(contentRef.getContentPropertyName());
                if (cp != null) {
                    contentRef.setFileName(cp.getFileName());
                }
            }));

            return contentRef;
        });
    }

    @Override
    public ContentRef createEphemeralNode(DocumentStream documentStream) {
        return createEphemeralNode(documentStream, null);
    }

    @Override
    public ContentRef createEphemeralNode(DocumentStream documentStream, InputNodeRequest extra) {
        var descriptor = new ContentDescriptor();
        descriptor.setName(CM_CONTENT);
        descriptor.setFileName(documentStream.getFileName());
        descriptor.setMimetype(documentStream.getMimeType());
        return createEphemeralNode(descriptor, extra, documentStream.getInputStream(), null);
    }

    @Override
    public void unephemeralize(String ephemeralUuid) {
        if (ephemeralUuid != null && sessionContext.getTenantData().map(TenantData::isTempEphemeralDisabled).orElse(false)) {
            transactionManagerPort.doOnTemp(() -> transactionManagerPort.performNew(tx -> {
                var inputNodeRequest = new InputNodeRequest();
                inputNodeRequest.getAspectOperations().put(ASPECT_ECMSYS_EPHEMERAL, InputNodeRequest.AspectOperation.REMOVE);
                inputNodeRequest.getAspectOperations().put(ASPECT_ECMSYS_EXPIRABLE, InputNodeRequest.AspectOperation.REMOVE);
                inputNodeRequest.getProperties().put(PROP_ECMSYS_EXPIRES_AT, null);
                nodeManager.updateNode(ephemeralUuid, inputNodeRequest, Set.of());
                return PerformResult.<Void>builder()
                    .mode(PerformResult.Mode.SYNC)
                    .count(1)
                    .tx(tx.getId())
                    .priorityUUIDs(Set.of(ephemeralUuid))
                    .build();
            }));
        }
    }
}
