package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.api.v2.rest.dto.QueryParameters;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.node.DeleteMode;
import it.doqui.libra.librabl.views.node.InputNodeRequest;
import it.doqui.libra.librabl.views.node.NodeOperation;
import it.doqui.libra.librabl.views.node.OperationOption;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public interface MultipleNodeOperationService {
    AsyncOperation<?> performOperations(Collection<NodeOperation> operations, Long limit, @NotNull OperationMode mode);
    AsyncOperation<?> performOperations(Collection<NodeOperation> operations, Long limit, @NotNull OperationMode mode, long delay);
    int updateNodes(QueryParameters queryParameters, InputNodeRequest input, @NotNull Set<OperationOption> optionSet);
    int deleteNodes(QueryParameters queryParameters, DeleteMode mode);
    int findNodesAndPerform(QueryParameters queryParameters, Function<Collection<String>, Integer> f);
}
