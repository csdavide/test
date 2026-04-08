package it.doqui.libra.librabl.application.model.jobs.responses;

public interface PartiallyCompletable {
    double getCompleteness();
    boolean isAborted();
    default boolean isCompleted() { return true; }
}
