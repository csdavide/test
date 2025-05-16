package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.Association;
import it.doqui.libra.librabl.api.v1.cxf.impl.AssociationServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.AsyncOpServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.NodeServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.*;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

@ApplicationScoped
@Slf4j
public class AssociationBusinessComponent implements BusinessComponent {

    @Inject
    AssociationServiceBridge associationService;

    @Inject
    NodeServiceBridge nodeService;

    @Inject
    AsyncOpServiceBridge asyncService;

    @Inject
    DtoMapper dtoMapper;

    @Override
    public Class<?> getComponentInterface() {
        return NodesBusinessInterface.class;
    }

    public GetAssociationsResponse getAssociations(String uid, String associationType, AssociationsSearchParams searchParams) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var params = dtoMapper.convert(searchParams, it.doqui.index.ecmengine.mtom.dto.AssociationsSearchParams.class);
        params.setAssociationType(associationType);
        var response = associationService.getAssociations(id, params, null);
        var result = new GetAssociationsResponse();
        result.setTotalResults(response.getTotalResults());
        if (response.getAssociationArray() != null) {
            result.setAssociations(
                Arrays.stream(response.getAssociationArray())
                    .map(item -> dtoMapper.convert(item, ResultAssociation.class))
                    .toList()
            );
        } else {
            result.setAssociations(List.of());
        }

        return result;
    }

    public void modifyAssociation(String uid, ModifyAssociationRequest associationInfo) {
        if (associationInfo == null || associationInfo.getAction() == null) {
            throw new BadRequestException("Null action");
        }

        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var targetId = new it.doqui.index.ecmengine.mtom.dto.Node(associationInfo.getTargetUid());
        var association = convert(associationInfo.getAssociation());
        switch (associationInfo.getAction()) {
            case COPY -> nodeService.copyNodeCopyChildren(id, targetId, true, association, null);
            case MOVE -> associationService.moveNodeAssociation(id, targetId, association, null);
            case LINK -> associationService.linkContent(id, targetId, association, null);
            case UNLINK -> associationService.unLinkContent(id, targetId, association, null);
            default -> throw new BadRequestException("Invalid 'action' value: " + associationInfo.getAction());
        }
    }

    public String modifyAssociationJob(String uid, ModifyAssociationJobRequest associationInfo) {
        if (associationInfo == null || associationInfo.getAction() == null) {
            throw new BadRequestException("Null action");
        }

        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var targetId = new it.doqui.index.ecmengine.mtom.dto.Node(associationInfo.getTargetUid());
        var association = convert(associationInfo.getAssociation());
        var job = switch (associationInfo.getAction()) {
            case LINKJOB -> asyncService.linkContentJob(id, targetId, association, null);
            case MOVEJOB -> asyncService.moveNodeJob(id, targetId, association, null);
        };

        return job.getName();
    }

    public void renameContent(String uid, RenameContentRequest renameParams) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        associationService.renameContent(
            id,
            renameParams.getNameValue(),
            renameParams.getPropertyPrefixedName(),
            renameParams.isOnlyPrimaryAssociation(),
            null);
    }

    private Association convert(it.doqui.libra.librabl.api.v1.rest.dto.Association association) {
        // se i seguenti attributi non sono valorizzati, esegue la chiamata senza
        // passare
        // l'oggetto association
        if (association != null && association.getTypePrefixedName() == null && association.getPrefixedName() == null) {
            association = null;
        }

        final Association _association;
        if (association != null) {
            _association = dtoMapper.convert(association, Association.class);
        } else {
            _association = null;
        }

        return _association;
    }
}
