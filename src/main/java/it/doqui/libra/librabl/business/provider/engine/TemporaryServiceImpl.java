package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.document.DocumentStream;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.interfaces.TemporaryService;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.*;
import it.doqui.libra.librabl.views.tenant.TenantData;
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

import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.PROP_ECMSYS_EXPIRES_AT;

@ApplicationScoped
@Slf4j
public class TemporaryServiceImpl implements TemporaryService {

    @ConfigProperty(name = "libra.paths.temp", defaultValue = "/app:company_home/cm:temp/")
    String tempHome;

    @Inject
    NodeManager nodeManager;

    @Inject
    TransactionService transactionService;

    @Override
    public String getTemporaryTenant() {
        return UserContextManager.getTenantData().map(TenantData::getTemp).orElse(null);
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

        return TransactionService.current().doOnTemp(() -> {
            var contentRef = new ContentRef();
            contentRef.setTenant(UserContextManager.getTenant());
            contentRef.setIdentity(UserContextManager.getContext().getAuthorityRef().getIdentity());
            contentRef.setContentPropertyName(contentStream.getName());
            contentRef.setUuid(nodeManager.createNode(input, (tx,node) -> {
                var cp = node.getData().getContentProperty(contentRef.getContentPropertyName());
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
        if (ephemeralUuid != null && UserContextManager.getTenantData().map(TenantData::isTempEphemeralDisabled).orElse(false)) {
            TransactionService.current().doOnTemp(() -> transactionService.performNew(tx -> {
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
