package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.VersionDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.VersionDetails;
import it.doqui.libra.librabl.business.provider.mappers.NodeMapper;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.VersionService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.version.VersionItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;

@ApplicationScoped
@Slf4j
public class VersionServiceImpl implements VersionService {

    @Inject
    SimpleNodeAccessManager simpleNodeAccessManager;

    @Inject
    VersionDAO versionDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    NodeValidator nodeValidator;

    @Inject
    ModelManager modelManager;

    @Inject
    ContentRetriever contentRetriever;

    @Override
    public List<VersionItem> listNodeVersions(String uuid, List<String> tags) {
        var node = simpleNodeAccessManager.getNode(uuid, PermissionFlag.R);
        var versions = versionDAO.listNodeVersions(node.getId(), tags);
        for (var v : versions) {
            v.setNodeUUID(uuid);
        }

        return versions;
    }

    @Override
    public Optional<VersionItem> createNodeVersion(String uuid, String tag) {
        var node = simpleNodeAccessManager.getNode(uuid, PermissionFlag.R);
        return versionDAO.createNodeVersion(node, tag);
    }

    @Override
    public void alterTagVersion(String uuid, int version, String tag) {
        var node = simpleNodeAccessManager.getNode(uuid, null);
        var item = versionDAO.getNodeVersion(node.getId(), version)
            .map(VersionDetails::getItem)
            .orElseThrow(() -> new NotFoundException("Version not found"));

        permissionValidator.requirePermission(node, item.getVersionTag() == null ? PermissionFlag.R : PermissionFlag.A);
        if (!versionDAO.alterTagVersion(node.getId(), version, tag)) {
            throw new SystemException("Unable to update version tag");
        }
    }

    @Override
    public Optional<VersionItem> getNodeVersion(String uuid, int version, Set<MapOption> optionSet, Locale locale) {
        var node = simpleNodeAccessManager.getNode(uuid, optionSet, PermissionFlag.R);
        return versionDAO.getNodeVersion(node.getId(), version)
            .map(v -> combine(v, node, optionSet, locale));

    }

    @Override
    public Optional<VersionItem> getNodeVersion(String versionUUID, Set<MapOption> optionSet, Locale locale) {
        return versionDAO.getNodeVersion(versionUUID)
            .map(v -> {
                var node = nodeDAO
                    .findNodeById(v.getItem().getNodeId(), optionSet, QueryScope.DEFAULT)
                    .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                    .orElseThrow(() -> new NotFoundException("Unable to find node " + v.getItem().getNodeId()));
                return combine(v, node, optionSet, locale);
            });
    }

    @Override
    public NodeAttachment getVersionedContent(String uuid, int version, String contentPropertyName, String fileName) {
        var node = simpleNodeAccessManager.getNode(uuid, Set.of(MapOption.DEFAULT), PermissionFlag.R);
        var data = versionDAO.getNodeVersion(node.getId(), version)
            .map(VersionDetails::getData)
            .orElseThrow(() -> new NotFoundException("Version not found"));

        try {
            return contentRetriever.retrieveContent(data, contentPropertyName, fileName);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public NodeAttachment getVersionedContent(String versionUUID, String contentPropertyName, String fileName) {
        return versionDAO.getNodeVersion(versionUUID)
            .map(v -> {
                var node = nodeDAO
                    .findNodeById(v.getItem().getNodeId(), Set.of(MapOption.DEFAULT), QueryScope.DEFAULT)
                    .map(n -> permissionValidator.requirePermission(n, PermissionFlag.R))
                    .orElseThrow(() -> new NotFoundException("Unable to find node " + v.getItem().getNodeId()));
                try {
                    return contentRetriever.retrieveContent(v.getData(), contentPropertyName, fileName);
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
    public void replaceNodeMetadata(String uuid, String sourceUUID, Integer sourceVersion) {
        log.debug("Replacing node {} with {} version {}", uuid, sourceUUID, sourceVersion);
        TransactionService.current().perform(tx -> {
            var node = simpleNodeAccessManager.getNode(uuid, Set.of(MapOption.DEFAULT), PermissionFlag.W);
            final ActiveNode sourceNode;
            var validationRequired = false;
            if (sourceUUID != null && !StringUtils.equals(sourceUUID, uuid)) {
                sourceNode = simpleNodeAccessManager.getNode(sourceUUID, Set.of(MapOption.DEFAULT), PermissionFlag.R);
                validationRequired = true;
            } else if (sourceVersion != null) {
                permissionValidator.requirePermission(node, PermissionFlag.R);
                sourceNode = node;
            } else {
                throw new BadRequestException("Either source uuid or source version must be specified");
            }

            var wasAutoVersion = ObjectUtils.getAsBoolean(node.getProperties().get(CM_AUTO_VERSION), false);
            nodeDAO.decrementContentRef(node);
            if (sourceVersion != null) {
                versionDAO.getNodeVersion(sourceNode.getId(), sourceVersion)
                    .ifPresentOrElse(v -> node.getData().replaceWith(v.getData()), () -> {
                    throw new PreconditionFailedException("Version not found");
                });
            } else {
                node.getData().replaceWith(sourceNode.getData());
            }

            // remove working copy attributes
            node.getData().getAspects().remove(ASPECT_CM_WORKINGCOPY);
            node.getData().getProperties().remove(CM_WORKINGCOPY_OWNER);
            if (StringUtils.equals(ObjectUtils.getAsString(node.getData().getProperties().get(PROP_CM_COPIED_NODE)), uuid)) {
                node.getData().getProperties().remove(PROP_CM_COPIED_NODE);
                node.getData().getAspects().remove(ASPECT_COPIED_NODE);
                var name = Optional.ofNullable(node.getData().getProperties().get(CM_NAME)).map(Object::toString).orElse("");
                var wcp = name.indexOf("_wc_");
                if (wcp != -1) {
                    node.getData().getProperties().put(CM_NAME, name.substring(0, wcp));
                }
            }

            // keep auto version if present in the original node
            if (wasAutoVersion) {
                node.getData().getAspects().add(ASPECT_CM_VERSIONABLE);
                node.getData().getProperties().put(CM_AUTO_VERSION, true);
            }

            if (validationRequired) {
                nodeValidator.validateMetadata(modelManager.getContextModel(), node);
            }

            simpleNodeAccessManager.updateNode(tx, node);
            nodeDAO.incrementContentRef(node);
            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }
}
