package it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing;

public class IndexingException extends RuntimeException {
    public IndexingException(Throwable e) {
        super(e);
    }
}
