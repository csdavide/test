package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.Content;
import it.doqui.index.ecmengine.mtom.dto.MassiveParameter;
import it.doqui.libra.librabl.api.v1.cxf.impl.ContentServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.NodeServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.NodesBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.MassiveDeleteNodeAction;
import it.doqui.libra.librabl.api.v1.rest.dto.Node;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
@Slf4j
public class MassiveBusinessComponent implements BusinessComponent {

    @Inject
    NodeServiceBridge nodeService;

    @Inject
    ContentServiceBridge contentService;

    @Inject
    DtoMapper dtoMapper;

    @Override
    public Class<?> getComponentInterface() {
        return NodesBusinessInterface.class;
    }

    public Node[] massiveCreateNode(
        String[] parentNodeUids, Node[] nodesRequests, Map<Integer, byte[]> contents,
        boolean oldImplementation, boolean synchronousReindex) {
        var parentArray = new it.doqui.index.ecmengine.mtom.dto.Node[parentNodeUids.length];
        for (int i = 0; i < parentNodeUids.length; i++) {
            parentArray[i] = new it.doqui.index.ecmengine.mtom.dto.Node(parentNodeUids[i]);
        }

        var itemArray = new Content[nodesRequests.length];
        for (int i = 0; i < nodesRequests.length; i++) {
            itemArray[i] = dtoMapper.convert(nodesRequests[i], Content.class);
        }

        var params = new MassiveParameter();
        params.setOldImplementation(oldImplementation);
        params.setSynchronousReindex(synchronousReindex);
        var createdNodes = nodeService.massiveCreateContent(parentArray, itemArray, params, null);
        return Arrays.stream(createdNodes)
            .map(it.doqui.index.ecmengine.mtom.dto.Node::getUid)
            .map(uuid -> {
                var node = new Node();
                node.setUid(uuid);
                return node;
            })
            .toList()
            .toArray(new Node[0]);
    }

    public Node[] massiveGetContentMetadata(List<String> uids) {
        var idArray = uids.stream()
            .map(it.doqui.index.ecmengine.mtom.dto.Node::new)
            .toList()
            .toArray(new it.doqui.index.ecmengine.mtom.dto.Node[0]);
        return map(nodeService.massiveGetContentMetadata(idArray, null));
    }

    public void massiveUpdateMetadata(List<Node> nodes) {
        nodeService.massiveUpdateMetadata(idArrayFromNodes(nodes), map(nodes), null);
    }

    public File massiveRetrieveContentData(List<Node> nodes) throws IOException {
        var files = contentService.massiveRetrieveFiles(idArrayFromNodes(nodes), map(nodes), null);
        var tmpPath = Files.createTempFile("content-", "-bytes");

        try (var zos = new ZipOutputStream(Files.newOutputStream(tmpPath))) {
            for (var file : files) {
                try (var is = Files.newInputStream(file.toPath())) {
                    var entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    for (int c = is.read(); c != -1; c = is.read()) {
                        zos.write(c);
                    }
                    zos.flush();
                }
            }
        }

        return tmpPath.toFile();
    }

    public void massiveDeleteNode(List<String> uids, MassiveDeleteNodeAction action) {
        var mode = switch (action) {
            case DELETE -> 0;
            case DELETE_AND_PURGE -> 1;
            case DELETE_AND_PURGE_AND_REMOVE -> 2;
        };
        nodeService.massiveDeleteNode(idArrayFromStrings(uids), mode, null);
    }

    private it.doqui.index.ecmengine.mtom.dto.Node[] idArrayFromNodes(List<Node> nodes) {
        return nodes.stream()
            .map(Node::getUid)
            .map(it.doqui.index.ecmengine.mtom.dto.Node::new)
            .toList()
            .toArray(new it.doqui.index.ecmengine.mtom.dto.Node[0]);
    }

    private it.doqui.index.ecmengine.mtom.dto.Node[] idArrayFromStrings(List<String> uids) {
        return uids.stream()
            .map(it.doqui.index.ecmengine.mtom.dto.Node::new)
            .toList()
            .toArray(new it.doqui.index.ecmengine.mtom.dto.Node[0]);
    }

    private Node[] map(Content[] items) {
        if (items == null) {
            return new Node[0];
        }

        var result = new Node[items.length];
        for (int i = 0; i < items.length; i++) {
            result[i] = dtoMapper.convert(items[i], Node.class);
        }

        return result;
    }

    private Content[] map(List<Node> nodes) {
        if (nodes == null) {
            return new Content[0];
        }

        return nodes.stream()
            .map(node -> dtoMapper.convert(node, Content.class))
            .toList()
            .toArray(new Content[0]);
    }
}
