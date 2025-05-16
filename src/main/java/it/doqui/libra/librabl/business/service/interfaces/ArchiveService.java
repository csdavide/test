package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.node.NodeItem;

import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface ArchiveService {
    Optional<NodeItem> getNode(String uuid, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale);
    Paged<NodeItem> findNodes(Collection<String> uuid, Collection<String> types, Collection<String> aspects, boolean includeMetadata, @NotNull Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, boolean excludeDescendants, Pageable pageable);
    void purgeNode(@NotNull String uid, boolean remove);
    void restoreNode(String uuid, LinkItemRequest destination, LinkMode mode);
}
