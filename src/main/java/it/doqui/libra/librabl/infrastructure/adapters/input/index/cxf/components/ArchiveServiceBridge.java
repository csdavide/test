package it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components;

import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.mappers.ContentMapper;
import it.doqui.libra.librabl.application.ports.in.ArchiveUseCase;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static it.doqui.libra.librabl.domain.policy.MapOption.*;

@IfBuildProperty(name = "libra.module.cxf.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class ArchiveServiceBridge extends AbstractServiceBridge {

    @Inject
    ContentMapper contentMapper;

    @Inject
    ArchiveUseCase archiveService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public SearchResponse listDeletedNodes(NodeArchiveParams params, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, SearchException {

        return call(context, () -> {
            Paged<NodeItem> p = list(params, true);
            SearchResponse result = new SearchResponse();
            result.setTotalResults((int)p.getTotalElements());
            result.setPageIndex((int)p.getPage());
            result.setPageSize((int)p.getSize());

            result.setContentArray(
                p.getItems()
                    .stream()
                    .map(n -> contentMapper.asContent(n))
                    .toList()
                    .toArray(new Content[0])
            );

            return result;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public NodeResponse listDeletedNodesNoMetadata(NodeArchiveParams params, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException {

        validate(() -> Objects.requireNonNull(params, "Params must not be null"));
        return call(context, () -> {
            Paged<NodeItem> p = list(params, false);
            NodeResponse result = new NodeResponse();
            result.setTotalResults((int)p.getTotalElements());
            result.setPageIndex((int)p.getPage());
            result.setPageSize((int)p.getSize());

            result.setNodeArray(
                p.getItems().stream()
                    .map(node -> new Node(node.getUuid()))
                    .toList()
                    .toArray(new Node[0])
            );

            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void purgeContent(Node node, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        purgeNode(node, false, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void purgeNode(Node node, boolean remove, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, DeleteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(node);
        call(context, () -> {
            archiveService.purgeNode(new Vertex(VertexType.UUID, node.getUid()), remove);
            return null;
        });
    }

    private Paged<NodeItem> list(NodeArchiveParams params, boolean includeMetadata)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException {

        final Pageable pageable;
        if (params.getPageSize() > 0) {
            pageable = new Pageable();
            pageable.setSize(params.getPageSize());
            pageable.setPage(params.getPageIndex());
        } else if (params.getLimit() > 0) {
            pageable = new Pageable();
            pageable.setSize(params.getLimit());
        } else {
            pageable = null;
        }

        Collection<String> types = params.getTypePrefixedName() == null ? null : List.of(params.getTypePrefixedName());
        return archiveService.findNodes(
            null,
            params.isTypeAsAspect() ? null : types,
            !params.isTypeAsAspect() ? null : types,
            includeMetadata,
            Set.of(SYS_PROPERTIES, NO_NULL_PROPERTIES, PARENT_ASSOCIATIONS, LEGACY),
            null,
            Locale.getDefault(),
            true,
            pageable);
    }
}
