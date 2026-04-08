package it.doqui.libra.librabl.application.model.messaging;

public interface MessageType {
    String REINDEX = "reindex";
    String MULTINODE = "multi-node";
    String OPERATIONS = "operations";
    String OPERATION = "operation";
    String JOB = "job";
    String DISTRIBUTED_EVENT = "event";
    String TRACE_EVENT = "trace";
    String NODES_CLEAN = "nodes-clean";
}
