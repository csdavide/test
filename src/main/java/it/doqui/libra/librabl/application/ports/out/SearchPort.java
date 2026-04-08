package it.doqui.libra.librabl.application.ports.out;

import it.doqui.libra.librabl.application.model.graph.NodeItem;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.SortDefinition;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface SearchPort {
    Paged<NodeItem> findNodes(@NotNull String q, List<SortDefinition> sortFields, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, Pageable pageable) throws SearchEngineException, IOException;
    Paged<String> findNodes(@NotNull String q, List<SortDefinition> sortFields, Pageable pageable) throws SearchEngineException, IOException;
}
