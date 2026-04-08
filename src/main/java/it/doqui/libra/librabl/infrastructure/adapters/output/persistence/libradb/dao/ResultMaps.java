package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.SecurityGroup;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.graph.Vertex;

import java.util.HashMap;
import java.util.Map;

public record ResultMaps(Map<Long, ActiveNode> idMap, Map<String, ActiveNode> uuidMap, Map<String, ActiveNode> pathMap,
                         Map<Long, SecurityGroup> sgMap, Map<Long, ApplicationTransaction> txMap) {

    public ResultMaps() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public ActiveNode getNode(Vertex vertex) {
        return switch (vertex.getType()) {
            case ID -> idMap.get(Long.parseLong(vertex.getValue()));
            case UUID -> uuidMap.get(vertex.getValue());
            case PATH -> pathMap.get(vertex.getValue());
        };
    }

}
