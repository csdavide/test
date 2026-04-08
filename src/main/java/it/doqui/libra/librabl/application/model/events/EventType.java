package it.doqui.libra.librabl.application.model.events;

public interface EventType {
    String RELOAD_TENANT = "reload-tenant";
    String RELOAD_MIMETYPES = "reload-mimetypes";
    String CLEAN_CACHE = "clean-cache";
    String SYSTEM_CHECK = "system-check";
    String JOB_ABORT = "job-abort";
}
