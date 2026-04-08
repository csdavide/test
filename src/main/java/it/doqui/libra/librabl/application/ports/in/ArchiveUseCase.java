package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface ArchiveUseCase {
    Optional<NodeItem> getNode(Vertex node, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale);
    Paged<NodeItem> findNodes(Collection<String> uuid, Collection<String> types, Collection<String> aspects, boolean includeMetadata, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, boolean excludeDescendants, Pageable pageable);
    void purgeNode(@NotNull Vertex node, boolean remove);
    void restoreNode(@NotNull Vertex node, ParentLink destination, LinkMode mode);
}
