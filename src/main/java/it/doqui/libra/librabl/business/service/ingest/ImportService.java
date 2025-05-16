package it.doqui.libra.librabl.business.service.ingest;

import java.util.List;
import java.util.Map;

public interface ImportService {
    List<String> importDataSet(String dataSetType, Map<String,Object> dataSet);
}
