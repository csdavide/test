package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.node.*;
import jakarta.validation.constraints.NotNull;

import java.util.*;

public interface NodeService {
    Optional<NodeItem> getNodeMetadata(@NotNull String uuid, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale);
    Optional<NodeItem> getNodeMetadata(long id, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale);
    Optional<NodeInfoItem> getNodeInfo(@NotNull String uuid);
    List<NodeItem> listNodeMetadata(@NotNull Collection<String> uuids, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, QueryScope scope);
    List<NodePathItem> listNodePaths(@NotNull String uuid);
    String createNode(@NotNull LinkedInputNodeRequest input);
    List<String> createNodes(@NotNull List<LinkedInputNodeRequest> input);
    void updateNode(String uuid, InputNodeRequest input, @NotNull Set<OperationOption> optionSet);
    void updateNodes(Collection<InputIdentifiedNodeRequest> inputs, @NotNull Set<OperationOption> optionSet);
    String copyNode(String uuid, LinkItemRequest destination, boolean includeChildren, boolean includeAssociations, CopyMode copyMode);
    void deleteNode(@NotNull String uuid, DeleteMode deleteMode);
    void renameNode(@NotNull String uuid, String name, LinkMode mode);
}
