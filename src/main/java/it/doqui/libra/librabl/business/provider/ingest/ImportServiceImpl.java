package it.doqui.libra.librabl.business.provider.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octomix.josson.Josson;
import it.doqui.libra.librabl.business.provider.data.dao.DictDAO;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.ingest.ImportService;
import it.doqui.libra.librabl.business.service.interfaces.NodeService;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@ApplicationScoped
@Slf4j
public class ImportServiceImpl implements ImportService {

    @Inject
    DictDAO dictDAO;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NodeService nodeService;

    @Override
    public List<String> importDataSet(String dataSetType, Map<String, Object> dataSet) {
        var exp = dictDAO.getPayload("import.dataset", dataSetType).orElseThrow(NotFoundException::new);
        var josson = Josson.from(dataSet);
        var records = josson.getArrayNode(exp);
        log.debug("Processing {} records", records.size());
        return TransactionService.current().perform(tx -> {
            try {
                var createdUUIDs = new ArrayList<String>();
                for (var record : records) {
                    var input = objectMapper.treeToValue(record, LinkedInputNodeRequest.class);
                    log.debug("Creating node from input '{}'", input);
                    createdUUIDs.add(nodeService.createNode(input));
                }

                return PerformResult.<List<String>>builder()
                    .mode(PerformResult.Mode.SYNC)
                    .priorityUUIDs(new HashSet<>(createdUUIDs))
                    .result(createdUUIDs)
                    .build();
            } catch (JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }
}
