package it.doqui.libra.librabl.business.provider.integration.messaging.events;

public interface EventType {
    String RELOAD_TENANT = "reload-tenant";
    String CLEAN_CACHE = "clean-cache";
    String SYSTEM_CHECK = "system-check";
}
