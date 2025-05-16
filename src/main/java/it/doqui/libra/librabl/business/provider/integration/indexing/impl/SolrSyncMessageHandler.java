package it.doqui.libra.librabl.business.provider.integration.indexing.impl;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.TenantService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Unremovable
@Slf4j
public class SolrSyncMessageHandler implements MessageHandler {

    @Inject
    TenantService tenantService;

    @Override
    public void handleMessage(Message message) {
        tenantService.syncTenant(UserContextManager.getContext().getTenantRef(), true);
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}
