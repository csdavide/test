package it.doqui.libra.librabl.business.provider.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.Startup;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.provider.schema.impl.SchemaManager;
import it.doqui.libra.librabl.business.service.interfaces.TenantService;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.views.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static it.doqui.libra.librabl.business.provider.schema.impl.TenantSchema.COMMON_SCHEMA;

@ApplicationScoped
@Startup
@Slf4j
public class Bootstrapper {

    @Inject
    @Any
    Event<BootEvent> event;

    @Inject
    SchemaManager schemaManager;

    @Inject
    TenantDataManager tenantManager;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "libra.locale", defaultValue = "it_IT")
    String locale;

    @ConfigProperty(name = "libra.boot.force-initialization", defaultValue = "false")
    boolean forceInitialization;

    @ConfigProperty(name = "libra.boot.update-common-models", defaultValue = "true")
    boolean updateCommonModels;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @Getter
    private String instanceId;

    @PostConstruct
    void init() throws UnknownHostException {
        instanceId = InetAddress.getLocalHost().getHostName() + "_" + UUID.randomUUID();
        log.info("Starting instance {}", instanceId);

        Locale.setDefault(I18NUtils.parseLocale(locale));
        log.info("Default locale is {}", Locale.getDefault());

        log.info("Loading all tenant schema");
        var initializationRequired = forceInitialization;
        try {
            if (!initializationRequired && updateCommonModels) {
                if (tenantManager.updateCommonModelsIfRequired() < 0) {
                    initializationRequired = true;
                }
            }

            if (!initializationRequired) {
                var common = schemaManager.loadTenant(TenantRef.valueOf(COMMON_SCHEMA), masterSchema, false);
                if (common.getNamespaces().isEmpty()) {
                    log.info("No namespace found on common schema");
                    initializationRequired = true;
                }
            }
        } catch (SystemException e) {
            if (e.getCause() instanceof SQLException) {
                log.error("Failed to load common schema (the master schema could be empty): {}", e.getMessage());
                initializationRequired = true;
            } else {
                throw e;
            }
        }

        if (initializationRequired) {
            if (forceInitialization) {
                log.info("Forced initialization");
            } else {
                log.info("Initialization required");
            }

            tenantManager.initialize(initializationRequired);
            log.info("Initialized");
            schemaManager.loadTenant(TenantRef.valueOf(COMMON_SCHEMA), masterSchema, false);
        }

        tenantManager.findAll()
            .stream()
            .filter(TenantSpace::isValid)
            .forEach(r -> {
                var t = TenantRef.valueOf(r.getTenant());
                log.debug("Processing tenant {}", t);
                schemaManager.loadTenant(t, r.getSchema(), false);
            });

        log.debug("Loaded tenant schemas {}", schemaManager.getTenantNames());
        schemaManager.getTenantNames()
                .stream()
                .map(tenant -> {
                    log.debug("Tenant schema {}", tenant);
                    return schemaManager.getTenantSchema(tenant);
                })
                .forEach(schema -> log.debug("Namespaces {}", schema.getNamespaces().keySet()));

        String description = "";
        var boot = new BootEvent();
        try {
            ClassLoader cl = this.getClass().getClassLoader();
            InputStream is = cl.getResourceAsStream("git.json");
            if (is != null) {
                var map = objectMapper.readValue(is, Map.class);
                var version = map.get("git.build.version");
                var time = map.get("git.build.time");
                var commitIdAbbrev = map.get("git.commit.id.abbrev");
                description = String.format("version %s%s built at %s", version, StringUtils.isBlank(commitIdAbbrev.toString()) ? "" : " " + commitIdAbbrev, time.toString());
                boot.setAttributes(map);
            }
        } catch (Exception e) {
            // ignore
        }

        log.info("Booted {}", description);
        event.fire(boot);

        if (!schemaManager.getTenantNames().contains(TenantRef.DEFAULT_TENANT)) {
            log.warn("No default tenant found. Creating it");
            var t = new TenantCreationRequest();
            t.setTenant(TenantRef.DEFAULT_TENANT);
            t.setSchema(masterSchema);
            var tenantService = Arc.container().select(TenantService.class).get();
            tenantService.createTenant(t);
        }
    }
}
