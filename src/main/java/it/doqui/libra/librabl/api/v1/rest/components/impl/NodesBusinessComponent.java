package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.Content;
import it.doqui.index.ecmengine.mtom.dto.DigestInfo;
import it.doqui.index.ecmengine.mtom.dto.NodeResponse;
import it.doqui.index.ecmengine.mtom.dto.SearchResponse;
import it.doqui.index.ecmengine.mtom.exception.SystemException;
import it.doqui.libra.librabl.api.v1.cxf.impl.ArchiveServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.DocumentServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.NodeServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.*;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;

import java.util.Arrays;

@ApplicationScoped
@Slf4j
public class NodesBusinessComponent implements BusinessComponent {

    @Inject
    NodeServiceBridge nodeService;

    @Inject
    ArchiveServiceBridge archiveService;

    @Inject
    DocumentServiceBridge documentService;

    @Inject
    DtoMapper dtoMapper;

    @Override
    public Class<?> getComponentInterface() {
        return NodesBusinessInterface.class;
    }

    public Node getContentMetadata(String uid) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var c = nodeService.getContentMetadata(id, null);
        return dtoMapper.convert(c, Node.class);
    }

    public String createContent(String parentNodeUid, Node node, byte[] bytes) {
        var content = dtoMapper.convert(node, it.doqui.index.ecmengine.mtom.dto.Content.class);
        content.setContent(bytes);
        var parent = new it.doqui.index.ecmengine.mtom.dto.Node(parentNodeUid);
        return nodeService.createContent(parent, content, null).getUid();
    }

    public void updateMetadata(String uid, Node node) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var content = dtoMapper.convert(node, it.doqui.index.ecmengine.mtom.dto.Content.class);
        nodeService.updateMetadata(id, content, null);
    }

    public void deleteNode(String uid, DeleteNodeAction action) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        switch (action) {
            case DELETE:
                nodeService.deleteNode(id, 0, null);
                break;

            case DELETE_AND_PURGE:
                nodeService.deleteNode(id, 1, null);
                break;

            case DELETE_AND_PURGE_AND_REMOVE:
                nodeService.deleteNode(id, 2, null);
                break;

            case PURGE:
                archiveService.purgeNode(id, false, null);
                break;

            case PURGE_AND_REMOVE:
                archiveService.purgeNode(id, true, null);
                break;

            default:
                throw new BadRequestException("Invalid 'action' value: " + action);
        }
    }

    public void restoreContent(String uid) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        nodeService.restoreContent(id, null);
    }

    public PathInfo[] getPaths(String uid) {
        var id = new it.doqui.index.ecmengine.mtom.dto.Node(uid);
        var paths = nodeService.getPaths(id, null);
        return Arrays.stream(paths)
            .map(path -> {
                var pathInfo = new PathInfo();
                pathInfo.setPath(path.getPath());
                pathInfo.setPrimary(path.isPrimary());
                return pathInfo;
            })
            .toList()
            .toArray(new PathInfo[0]);
    }

    public LuceneSearchResponse luceneSearch(SearchParams searchParams, String metadataItems) {
        var params = dtoMapper.convert(searchParams, it.doqui.index.ecmengine.mtom.dto.SearchParams.class);
        final LuceneSearchResponse response;
        if (metadataItems != null) {
            final SearchResponse r;
            try {
                r = nodeService.luceneSearch(params, null);
            } catch (SystemException e) {
                throw new it.doqui.libra.librabl.foundation.exceptions.SystemException(e);
            }

            response = map(r);
        } else {
            response = map(nodeService.luceneSearchNoMetadata(params, null));
        }

        return response;
    }

    public long getDbIdFromUID(String uid) {
        return nodeService.getDbIdFromUID(new it.doqui.index.ecmengine.mtom.dto.Node(uid), null);
    }

    public LuceneSearchResponse listDeletedNodes(NodeArchiveParams nodeArchiveParams, Boolean metadata) {
        var params = dtoMapper.convert(nodeArchiveParams, it.doqui.index.ecmengine.mtom.dto.NodeArchiveParams.class);
        final LuceneSearchResponse response;
        if (BooleanUtils.toBoolean(metadata)) {
            response = map(archiveService.listDeletedNodes(params, null));
        } else {
            response = map(archiveService.listDeletedNodesNoMetadata(params, null));
        }

        return response;
    }

    public boolean compareDigest(CompareDigestRequest compareDigestRequest, String algorithm) {
        var nodeInfo = new it.doqui.index.ecmengine.mtom.dto.NodeInfo();
        nodeInfo.setEnveloped(compareDigestRequest.getNodeInfo().isEnveloped());
        nodeInfo.setPrefixedName(compareDigestRequest.getNodeInfo().getContentPropertyName());

        var tempInfo = new it.doqui.index.ecmengine.mtom.dto.NodeInfo();
        tempInfo.setEnveloped(compareDigestRequest.getTempNodeInfo().isEnveloped());
        tempInfo.setPrefixedName(compareDigestRequest.getTempNodeInfo().getContentPropertyName());

        var nodeId = new it.doqui.index.ecmengine.mtom.dto.Node(compareDigestRequest.getNodeInfo().getUid());
        var tempId = new it.doqui.index.ecmengine.mtom.dto.Node(compareDigestRequest.getTempNodeInfo().getUid());

        var digestInfo = new DigestInfo();
        digestInfo.setAlgorithm(algorithm);

        return documentService.compareDigest(nodeId, tempId, nodeInfo, tempInfo, digestInfo, null);
    }

    public String createContentFromTemp(String parentNodeUid, String tempNodeUid, Node node) {
        var parent = new it.doqui.index.ecmengine.mtom.dto.Node(parentNodeUid);
        var temp = new it.doqui.index.ecmengine.mtom.dto.Node(tempNodeUid);
        var content = dtoMapper.convert(node, Content.class);
        var createdNode =  nodeService.createContentFromTemporaney(parent, content, null, temp);
        return createdNode.getUid();
    }

    private LuceneSearchResponse map(SearchResponse r) {
        var response = dtoMapper.convert(r, LuceneSearchResponse.class);
        response.setNodes(
            Arrays.stream(r.getContentArray())
                .map(item -> dtoMapper.convert(item, Node.class))
                .toList()
        );

        return response;
    }

    private LuceneSearchResponse map(NodeResponse r) {
        var response = dtoMapper.convert(r, LuceneSearchResponse.class);
        response.setNodes(
            Arrays.stream(r.getNodeArray())
                .map(item -> {
                    var node = new Node();
                    node.setUid(item.getUid());
                    return node;
                })
                .toList()
        );

        return response;
    }

}
