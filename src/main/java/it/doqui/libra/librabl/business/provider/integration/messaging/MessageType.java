package it.doqui.libra.librabl.business.provider.integration.messaging;

public interface MessageType {
    String REINDEX = "reindex";
    String REINDEX_RANGE = "reindex-range";
    String LINK = "link";
    String MULTILINK = "multi-link";
    String MULTINODE = "multi-node";
    String DISTRIBUTED_EVENT = "event";
    String TRACE_EVENT = "trace";
    String SOLR_SYNC = "solr-sync";
    String TX_CLEAN = "tx-clean";
    String NODES_CLEAN = "nodes-clean";
    String CALC_VOLUMES = "calc-volumes";
}
