package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.application.model.graph.VersionItem;
import it.doqui.libra.librabl.application.ports.in.VersioningUseCase;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeLoaderDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.QueryContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.VersionDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.VersionDetails;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.util.*;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;

@ApplicationScoped
@Slf4j
public class VersionService implements VersioningUseCase {

    @Inject
    SimpleNodeAccessManager simpleNodeAccessManager;

    @Inject
    VersionDAO versionDAO;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    NodeValidator nodeValidator;

    @Inject
    ModelManagerPort modelManager;

    @Inject
    AttachmentHelper contentRetriever;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Override
    public List<VersionItem> listNodeVersions(Vertex vertex, List<String> tags) {
        var node = simpleNodeAccessManager.getNode(vertex, PermissionFlag.R);
        var versions = versionDAO.listNodeVersions(node.getId(), tags);
        for (var v : versions) {
            v.setNodeUUID(node.getUuid());
        }

        return versions;
    }

    @Override
    public Optional<VersionItem> createNodeVersion(Vertex vertex, String tag) {
        var node = simpleNodeAccessManager.getNode(vertex, PermissionFlag.R);
        return versionDAO.createNodeVersion(node, tag);
    }

    @Override
    public void alterTagVersion(Vertex vertex, int version, String tag) {
        var node = simpleNodeAccessManager.getNode(vertex, null);
        var item = versionDAO.getNodeVersion(node.getId(), version)
            .map(VersionDetails::getItem)
            .orElseThrow(() -> new NotFoundException("Version not found"));

        permissionValidator.requirePermission(node, item.getVersionTag() == null ? PermissionFlag.R : PermissionFlag.A);
        if (!versionDAO.alterTagVersion(node.getId(), version, tag)) {
            throw new SystemException("Unable to update version tag");
        }
    }

    @Override
    public Optional<VersionItem> getNodeVersion(Vertex vertex, int version, Set<MapOption> optionSet, Locale locale) {
        var node = simpleNodeAccessManager.getNode(vertex, optionSet, PermissionFlag.R);
        return versionDAO.getNodeVersion(node.getId(), version)
            .map(v -> combine(v, node, optionSet, locale));

    }

    @Override
    public Optional<VersionItem> getNodeVersion(String versionUUID, Set<MapOption> optionSet, Locale locale) {
        return versionDAO.getNodeVersion(versionUUID)
            .map(v -> {
                var qc = QueryContext.builder()
                        .schema(sessionContext.getUserContext().getDbSchema())
                        .tenant(sessionContext.getTenant())
                        .optionSet(optionSet)
                        .scope(QueryScope.DEFAULT)
                        .build();
                var node = nodeLoaderDAO
                    .getNode(new Vertex(VertexType.ID, v.getItem().getNodeId()), qc)
                    .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                    .orElseThrow(() -> new NotFoundException("Unable to find node " + v.getItem().getNodeId()));
                return combine(v, node, optionSet, locale);
            });
    }

    @Override
    public NodeAttachment getVersionedContent(Vertex vertex, int version, String contentPropertyName, String fileName) {
        var node = simpleNodeAccessManager.getNode(vertex, Set.of(MapOption.DEFAULT), PermissionFlag.R);
        var data = versionDAO.getNodeVersion(node.getId(), version)
            .map(VersionDetails::getData)
            .orElseThrow(() -> new NotFoundException("Version not found"));

        try {
            var c = data.getContent(ContentReferenceable.of(contentPropertyName, fileName)).orElseThrow(PreconditionFailedException::new);
            return contentRetriever.attachment(data, c);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public NodeAttachment getVersionedContent(String versionUUID, String contentPropertyName, String fileName) {
        return versionDAO.getNodeVersion(versionUUID)
            .map(v -> {
                var qc = QueryContext.builder()
                        .schema(sessionContext.getUserContext().getDbSchema())
                        .tenant(sessionContext.getTenant())
                        .optionSet(Set.of(MapOption.DEFAULT))
                        .scope(QueryScope.DEFAULT)
                        .build();

                var node = nodeLoaderDAO
                    .getNode(new Vertex(VertexType.ID, "" + v.getItem().getNodeId()), qc)
                    .orElseThrow(() -> new NotFoundException("Related node " + v.getItem().getNodeId() + " not found"));
                permissionValidator.requirePermission(node, PermissionFlag.R);

                try {
                    var c = v.getData().getContent(ContentReferenceable.of(contentPropertyName, fileName)).orElseThrow(PreconditionFailedException::new);
                    return contentRetriever.attachment(v.getData(), c);
                } catch (IOException e) {
                    throw new SystemException(e);
                }
            })
            .orElseThrow(() -> new NotFoundException("Unable to get versioned content identified by " + versionUUID));
    }

    private VersionItem combine(VersionDetails v, ActiveNode node, Set<MapOption> optionSet, Locale locale) {
        node.getData().replaceWith(v.getData());
        node.setVersion(v.getItem().getVersion());
        var n = nodeMapper.asNodeItem(node, optionSet, null, locale);

        var result = v.getItem();
        result.setItem(n);
        return result;
    }

    @Override
    public void replaceNodeMetadata(Vertex vertex, Vertex source, Integer sourceVersion) {
        log.debug("Replacing node {} with {} version {}", vertex, source, sourceVersion);
        transactionManagerPort.perform(tx -> {
            var targetNode = simpleNodeAccessManager.getNode(vertex, Set.of(MapOption.DEFAULT), PermissionFlag.W, QueryScope.UPDATE);
            final ActiveNode sourceNode;
            var validationRequired = false;
            if (source != null && !source.equals(vertex)) {
                sourceNode = simpleNodeAccessManager.getNode(source, Set.of(MapOption.DEFAULT), PermissionFlag.R);
                if (Objects.equals(sourceNode.getId(), targetNode.getId())) {
                    if (sourceVersion == null) {
                        throw new BadRequestException("Source version must be specified when replacing node with itself");
                    }
                } else {
                    validationRequired = true;
                }
            } else if (sourceVersion != null) {
                permissionValidator.requirePermission(targetNode, PermissionFlag.R);
                sourceNode = targetNode;
            } else {
                throw new BadRequestException("Either source vertex or source version must be specified");
            }

            var wasAutoVersion = ObjectUtils.getAsBoolean(targetNode.getProperties().get(CM_AUTO_VERSION), false);
            nodeDAO.decrementContentRef(targetNode);
            if (sourceVersion != null) {
                versionDAO.getNodeVersion(sourceNode.getId(), sourceVersion)
                    .ifPresentOrElse(v -> targetNode.getData().replaceWith(v.getData()), () -> {
                    throw new PreconditionFailedException("Version not found");
                });
            } else {
                targetNode.getData().replaceWith(sourceNode.getData());
            }

            // remove working copy attributes
            targetNode.getData().getAspects().remove(ASPECT_CM_WORKINGCOPY);
            targetNode.getData().getProperties().remove(CM_WORKINGCOPY_OWNER);
            if (Strings.CS.equals(ObjectUtils.getAsString(targetNode.getData().getProperties().get(PROP_CM_COPIED_NODE)), targetNode.getUuid())) {
                targetNode.getData().getProperties().remove(PROP_CM_COPIED_NODE);
                targetNode.getData().getAspects().remove(ASPECT_COPIED_NODE);
                var name = Optional.ofNullable(targetNode.getData().getProperties().get(CM_NAME)).map(Object::toString).orElse("");
                var wcp = name.indexOf("_wc_");
                if (wcp != -1) {
                    targetNode.getData().getProperties().put(CM_NAME, name.substring(0, wcp));
                }
            }

            // keep auto version if present in the original node
            if (wasAutoVersion) {
                targetNode.getData().getAspects().add(ASPECT_CM_VERSIONABLE);
                targetNode.getData().getProperties().put(CM_AUTO_VERSION, true);
            }

            if (validationRequired) {
                nodeValidator.validateMetadata(modelManager.getContextModel(), targetNode);
            }

            simpleNodeAccessManager.updateNode(tx, targetNode);
            nodeDAO.incrementContentRef(targetNode);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }
}
