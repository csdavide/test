package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.application.model.graph.*;
import it.doqui.libra.librabl.domain.policy.*;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.application.model.statements.RenameStatement;
import jakarta.validation.constraints.NotNull;

import java.util.*;

public interface NodeUseCase {
    Optional<NodeItem> getNodeMetadata(@NotNull Vertex vertex, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale);
    List<NodeItem> listNodeMetadata(@NotNull Collection<String> uuids, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, QueryScope scope);
    List<NodePathItem> listNodePaths(@NotNull String uuid);
    String createOrUpdateNode(@NotNull LinkedInputNodeRequest input, @NotNull Set<OperationOption> optionSet);
    String createNode(@NotNull LinkedInputNodeRequest input);
    String createNode(@NotNull LinkedInputNodeRequest input, @NotNull Set<OperationOption> optionSet);
    List<String> createNodes(@NotNull List<LinkedInputNodeRequest> input, @NotNull Set<OperationOption> optionSet);
    void updateNode(String uuid, InputNodeRequest input, @NotNull Set<OperationOption> optionSet);
    void updateNodes(Collection<InputIdentifiedNodeRequest> inputs, @NotNull Set<OperationOption> optionSet);
    String copyNode(@NotNull Vertex vertex, @NotNull ParentLink parent, boolean includeChildren, boolean includeAssociations, CopyMode copyMode);
    void deleteNode(@NotNull String uuid, DeleteMode deleteMode);
    long renameNode(Vertex node, RenameStatement rename);
    long moveNode(Vertex node, ParentLink destination);
}
