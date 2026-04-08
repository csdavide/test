package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

public enum ReindexTable {
    NODES,
    ARC_NODES,
    TX_NODES,
    PATHS;

    public String mapToTableName(boolean withAlias) {
        return switch (this) {
            case NODES -> "ecm_nodes" + (withAlias ? " n" : "");
            case ARC_NODES -> "ecm_archived_nodes" + (withAlias ? " an" : "");
            case TX_NODES -> "ecm_transaction_nodes" + (withAlias ? " tn" : "");
            case PATHS -> "ecm_paths" +  (withAlias ? " p" : "");
        };
    }
}
