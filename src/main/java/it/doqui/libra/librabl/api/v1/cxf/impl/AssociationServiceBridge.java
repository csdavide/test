package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.business.service.interfaces.AssociationService;
import it.doqui.libra.librabl.business.service.interfaces.MultipleNodeOperationService;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.*;
import it.doqui.libra.librabl.views.node.NodeOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.views.node.NodeOperation.NodeOperationType.MOVE;
import static it.doqui.libra.librabl.views.node.NodeOperation.NodeOperationType.RENAME;

@ApplicationScoped
@Slf4j
public class AssociationServiceBridge extends AbstractServiceBridge {

    @Inject
    AssociationService associationService;

    @Inject
    MultipleNodeOperationService multipleNodeOperationService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Association[] getAssociationsAssocType(Node node, String assocType, int maxResults, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, SearchException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        var params = new AssociationsSearchParams();
        params.setLimit(maxResults);
        params.setAssociationType(assocType);
        var result = getAssociations(node, params, context);
        return result.getAssociationArray();
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public AssociationResponse getAssociations(
        Node node,
        AssociationsSearchParams associationsSearchParams,
        MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, SearchException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(node);
        validate(() -> {
            requireNonNull(associationsSearchParams, "AssociationsSearchParams");

            if (associationsSearchParams.getAssociationType() != null) {
                switch (associationsSearchParams.getAssociationType()) {
                    case "PARENT", "CHILD", "SOURCE", "TARGET":
                        break;
                    default:
                        throw new InvalidParameterException("Invalid associationType: it must be one of PARENT, CHILD, SOURCE, or TARGET");
                }
            }
        });

        return call(context, () -> {
            RelationshipKind relationship = null;
            Collection<String> filterTypes = null;
            if (associationsSearchParams.getAssociationType() != null) {
                relationship = RelationshipKind.valueOf(associationsSearchParams.getAssociationType());
            }

            if (associationsSearchParams.getFilterType() != null) {
                filterTypes = Arrays.stream(associationsSearchParams.getFilterType()).collect(Collectors.toList());
            }

            Pageable pageable = null;
            if (associationsSearchParams.getPageSize() > 0) {
                pageable = new Pageable();
                pageable.setSize(associationsSearchParams.getPageSize());
                pageable.setPage(associationsSearchParams.getPageIndex());
            } else if (associationsSearchParams.getLimit() > 0) {
                pageable = new Pageable();
                pageable.setSize(associationsSearchParams.getLimit());
            }

            Paged<AssociationItem> p = associationService.findAssociations(node.getUid(), relationship, null, filterTypes, pageable);

            AssociationResponse response = new AssociationResponse();
            response.setTotalResults(p.getTotalElements());
            response.setAssociationArray(
                p.getItems()
                    .stream()
                    .map(a -> new LinkItem(a, node.getUid()))
                    .map(x -> {
                        Association y = new Association();
                        y.setTypePrefixedName(x.getTypeName());
                        y.setPrefixedName(x.getName());
                        y.setTargetUid(x.getVertexUUID());
                        y.setChildAssociation(switch (x.getRelationship()) {
                            case PARENT,CHILD -> true;
                            case SOURCE,TARGET -> false;
                        });

                        return y;
                    }).toArray(Association[]::new)
            );
            return response;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void renameContent(Node source, String nameValue, String propertyPrefixedName,
                              boolean onlyPrimaryAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, UpdateException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(source);
        validate(() -> Objects.requireNonNull(nameValue));

        call(context, () -> {
            var association = new LinkItemRequest();
            association.setName(nameValue);

            var renameOp = new NodeOperation();
            renameOp.setUuid(source.getUid());
            renameOp.setAssociation(association);
            renameOp.setOp(RENAME);
            renameOp.setOperand(new NodeOperation.RenameOperand().setPropertyName(propertyPrefixedName).setMode(onlyPrimaryAssociation ? LinkMode.FIRST : LinkMode.ALL));
            try {
                multipleNodeOperationService.performOperations(List.of(renameOp), null, OperationMode.SYNC);
                return null;
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage(), e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void linkContent(Node source, Node destination, Association association, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(source);
        validate(destination);
        validate((() -> Objects.requireNonNull(association)));

        var uuid = destination.getUid();
        var link = asLink(source.getUid(), association, false);

        call(context, () -> {
            try {
                AsyncOperation<AssociationItem> f = associationService.linkNode(uuid, link, OperationMode.SYNC);
                return f.get();
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage(), e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void unLinkContent(Node source, Node destination, Association association, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        validate(source);
        validate(destination);
        validate((() -> {
            Objects.requireNonNull(association, "Association must not be null");
            //Objects.requireNonNull(association.getPrefixedName(), "Association name must not be null");
        }));

        call(context, () -> {
            var uuid = destination.getUid();
            var link = asLink(source.getUid(), association, false);
            try {
                associationService.unlinkNode(uuid, link);
            } catch (PreconditionFailedException e) {
                log.warn("Got precondition failed exception for uuid {} and link {}: {}", uuid, link, e.getMessage());
            }

            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void moveNode(Node node, Node parent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        moveNodeAssociation(node, parent, null, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void moveNodeAssociation(Node source, Node parent, Association newAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(source, "source");
        validate(parent, "parent");
        call(context, () -> {
            var moveOp = new NodeOperation();
            moveOp.setUuid(source.getUid());
            moveOp.setOp(MOVE);
            moveOp.setOperand(parent.getUid());

            if (newAssociation != null) {
                if (!newAssociation.isChildAssociation()) {
                    throw new BadRequestException("Unsupported operation: parent/child association is required");
                }

                var linkItem = asLink(parent.getUid(), newAssociation, true);
                moveOp.setAssociation(linkItem);
            }

            try {
                multipleNodeOperationService.performOperations(List.of(moveOp), null, OperationMode.SYNC);
                return null;
            } catch (ConflictException e) {
                throw new InsertException(e.getMessage(), e);
            }
        });
    }

}
