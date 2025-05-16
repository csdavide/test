package it.doqui.libra.librabl.business.provider.core;

import it.doqui.libra.librabl.business.service.core.PerformResult;

public interface TransactionContextOptions {
    void disableWithInTxMode();
    void registerCreatedContentUrl(String contentUrl);
    void setMode(PerformResult.Mode mode);
}
