package it.doqui.libra.librabl.infrastructure.platform.tx;

import it.doqui.libra.librabl.domain.model.session.PerformResult;

import java.util.Set;

public interface TransactionContextOptions {
    void disableWithInTxMode();
    void registerCreatedContentUrl(String contentUrl);
    void registerReplacedContentUrl(String contentUrl);
    Set<String> getCreatedFileSet();
    Set<String> getReplacedFileSet();
    void setMode(PerformResult.Mode mode);
}
