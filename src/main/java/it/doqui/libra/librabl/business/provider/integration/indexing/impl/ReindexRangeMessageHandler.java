package it.doqui.libra.librabl.business.provider.integration.indexing.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ReindexService;
import it.doqui.libra.librabl.views.management.MgmtOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@ApplicationScoped
@Unremovable
@Slf4j
public class ReindexRangeMessageHandler implements MessageHandler {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ReindexService reindexService;

    @Override
    public void handleMessage(Message message) throws JMSException {
        final MgmtOperation.ReindexOperand operand;
        try {
            var s = Objects.requireNonNull(message.getStringProperty("operand"), "No operand");
            operand = objectMapper.readValue(s, MgmtOperation.ReindexOperand.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        reindexService.reindex(UserContextManager.getContext().getTenantRef(), operand.getFrom(), operand.getTo(), operand.getFlags(), operand.getBlockSize(), operand.isAddOnly());
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}
