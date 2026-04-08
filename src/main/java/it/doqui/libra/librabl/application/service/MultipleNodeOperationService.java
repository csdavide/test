package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.model.query.QueryParameters;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.domain.model.exceptions.SearchEngineException;
import it.doqui.libra.librabl.application.ports.in.MultipleNodeOperationUseCase;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.ports.out.SearchPort;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@ApplicationScoped
@Slf4j
public class MultipleNodeOperationService implements MultipleNodeOperationUseCase {

    @Inject
    NodeUseCase nodeService;

    @Inject
    SearchPort searchPort;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public int updateNodes(QueryParameters queryParameters, InputNodeRequest input, Set<OperationOption> optionSet) {
        return transactionManagerPort.perform(tx -> {
            var count = findNodes(queryParameters, uuids -> {
                int c = 0;
                for (String uuid : uuids) {
                    nodeService.updateNode(uuid, input, optionSet);
                    c++;
                }
                return c;
            });

            return PerformResult.<Integer>builder().result(count).build();
        });
    }

    @Override
    public int deleteNodes(QueryParameters queryParameters, DeleteMode mode) {
        return transactionManagerPort.perform(tx -> {
            var count = findNodes(queryParameters, uuids -> {
                int c = 0;
                for (String uuid : uuids) {
                    nodeService.deleteNode(uuid, mode);
                    c++;
                }
                return c;
            });

            return PerformResult.<Integer>builder().result(count).build();
        });
    }

    @Override
    public int findNodes(QueryParameters queryParameters, Function<Collection<String>, Integer> f) {
        int count = 0;
        if (queryParameters.getUuids() != null) {
            if (f != null) {
                count += f.apply(queryParameters.getUuids());
            }
        } else {
            List<String> conditions = new ArrayList<>();
            if (StringUtils.isNotBlank(queryParameters.getQ())) {
                conditions.add(queryParameters.getQ());
            }

            if (StringUtils.isNotBlank(queryParameters.getPath())) {
                conditions.add(String.format("PATH:\"%s\"", queryParameters.getPath()));
            }

            if (StringUtils.isNotBlank(queryParameters.getRoute())) {
                conditions.add(String.format("NODEPATH:\"%s\"", queryParameters.getRoute()));
            }

            if (conditions.isEmpty()) {
                throw new BadRequestException("No query specified");
            }

            try {
                var q = String.join(" AND ", conditions);
                var pageable = new Pageable();
                pageable.setPage(0);
                pageable.setSize(100);
                Paged<String> p;
                do {
                    p = searchPort.findNodes(q, null, pageable);
                    if (f != null && !p.getItems().isEmpty()) {
                        count += f.apply(p.getItems());
                    }

                    pageable.setPage(pageable.getPage() + 1);
                } while (pageable.getPage() < p.getTotalPages());
            } catch (SearchEngineException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        return count;
    }

}
