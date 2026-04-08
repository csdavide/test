package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.application.model.query.QueryParameters;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public interface MultipleNodeOperationUseCase {
    int updateNodes(QueryParameters queryParameters, InputNodeRequest input, @NotNull Set<OperationOption> optionSet);
    int deleteNodes(QueryParameters queryParameters, DeleteMode mode);
    int findNodes(QueryParameters queryParameters, Function<Collection<String>, Integer> f);
}
