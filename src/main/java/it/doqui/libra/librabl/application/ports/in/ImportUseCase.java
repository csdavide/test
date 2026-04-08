package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.application.model.ingest.ImportSource;
import it.doqui.libra.librabl.application.model.ingest.ImportStatement;
import it.doqui.libra.librabl.application.model.jobs.responses.ImportResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;

import java.io.InputStream;
import java.time.Duration;
import java.util.function.Consumer;

public interface ImportUseCase {
    ImportResult importDataSet(ImportSource source, ImportStatement statement, Consumer<Long> countConsumer);
    JobResponse submitImportDataSet(InputStream inputStream, String mimeType, Duration duration, ImportStatement statement);
}
