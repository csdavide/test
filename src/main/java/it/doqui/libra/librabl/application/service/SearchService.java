package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.model.graph.NodeItem;
import it.doqui.libra.librabl.application.ports.in.SearchUseCase;
import it.doqui.libra.librabl.application.ports.out.SearchPort;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.SortDefinition;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class SearchService implements SearchUseCase {

    @Inject
    SearchPort searchPort;

    @Override
    public Paged<NodeItem> findNodes(String q, List<SortDefinition> sortFields, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, Pageable pageable) throws SearchEngineException, IOException {
        return searchPort.findNodes(q, sortFields, optionSet, filterPropertyNames, locale, pageable);
    }

    @Override
    public Paged<String> findNodes(String q, List<SortDefinition> sortFields, Pageable pageable) throws SearchEngineException, IOException {
        return searchPort.findNodes(q, sortFields, pageable);
    }
}
