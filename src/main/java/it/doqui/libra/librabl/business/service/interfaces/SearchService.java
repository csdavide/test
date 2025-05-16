package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.exceptions.SearchEngineException;
import it.doqui.libra.librabl.business.service.node.SortDefinition;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.node.NodeItem;
import jakarta.validation.constraints.NotNull;
import org.apache.solr.common.SolrDocument;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface SearchService {
    Paged<NodeItem> findNodes(@NotNull String q, List<SortDefinition> sortFields, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, Pageable pageable) throws SearchEngineException, IOException;
    Paged<String> findNodes(@NotNull String q, List<SortDefinition> sortFields, Pageable pageable) throws SearchEngineException, IOException;
    Paged<SolrDocument> findNodes(String q, List<SortDefinition> sortFields, Pageable pageable, List<String> returnFields) throws SearchEngineException, IOException;
}
