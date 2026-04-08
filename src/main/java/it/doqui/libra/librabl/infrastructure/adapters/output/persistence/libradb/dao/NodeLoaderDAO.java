package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.NodePath;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@ApplicationScoped
@Slf4j
public class NodeLoaderDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public Optional<ActiveNode> getNode(@NotNull ParentLink parentLink, QueryContext queryContext) {
        String path;
        if (parentLink.getParent().getType() != VertexType.PATH) {
            var parentNode = getNode(parentLink.getParent(), queryContext)
                    .orElseThrow(() -> new PreconditionFailedException("Unable to find parent node " + parentLink.getParent()));
            path = parentNode.getPaths()
                    .stream()
                    .filter(NodePath::isHard)
                    .map(NodePath::getFilePath)
                    .findFirst()
                    .orElseThrow(() -> new PreconditionFailedException("Unable to find any hard path of parent " + parentLink.getParent()));
        } else {
            path = parentLink.getParent().getValue();
            if (!path.endsWith("/")) {
                path += "/";
            }
        }

        path += parentLink.getName();
        path += "/";

        return getNode(new Vertex(VertexType.PATH, path), queryContext);
    }

    public Optional<ActiveNode> getNode(Vertex vertex, QueryContext queryContext) {
        var maps = lookupNodes(List.of(vertex), queryContext);
        return Optional.ofNullable(maps.getNode(vertex));
    }

    public List<ActiveNode> findNodes(Collection<Vertex> vertexes, QueryContext queryContext) {
        var maps = lookupNodes(vertexes, queryContext);
        return vertexes.stream().map(maps::getNode).filter(Objects::nonNull).toList();
    }

    public List<Optional<ActiveNode>> findOptionalNodes(Collection<Vertex> vertexes, QueryContext queryContext) {
        var maps = lookupNodes(vertexes, queryContext);
        var result = new ArrayList<Optional<ActiveNode>>(vertexes.size());
        for (var vertex : vertexes) {
            result.add(Optional.ofNullable(maps.getNode(vertex)));
        }
        return result;
    }

    public ResultMaps lookupNodes(Collection<Vertex> vertexes, QueryContext queryContext) {
        var loader = new NodeLoader(ds, objectMapper, queryContext, sessionContext.getTenantData().orElse(null));
        return loader.lookupNodes(vertexes);
    }

}
