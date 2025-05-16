package it.doqui.libra.librabl.business.provider.integration.indexing.impl;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.parse;

@ApplicationScoped
@Unremovable
@Slf4j
public class IndexingMessageHandler implements MessageHandler {

    @Inject
    Indexer indexer;

    @Override
    public void handleMessage(Message message) throws JMSException {
        String tenant = UserContextManager.getContext().getTenantRef().toString();
        final int flags = parse(Objects.requireNonNullElse(message.getStringProperty("flags"), "1111"));
        boolean completed = ObjectUtils.getAsBoolean(message.getObjectProperty("completed"), true);
        boolean addOnly = ObjectUtils.getAsBoolean(message.getObjectProperty("addOnly"), false);
        boolean recursive = ObjectUtils.getAsBoolean(message.getObjectProperty("recursive"), false);
        int blockSize = ObjectUtils.getAsInt(message.getObjectProperty("blockSize"), 40);
        var taskId = message.getStringProperty("taskId");

        var value = message.getStringProperty("tx");
        if (StringUtils.isNotBlank(value)) {
            List<Long> tx = Arrays.stream(StringUtils.stripToEmpty(value).split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

            indexer.reindexTransactions(taskId, tenant, null, tx, flags, getUUIDs(message.getObjectProperty("include")), getUUIDs(message.getObjectProperty("exclude")), true, completed, addOnly);
        } else {
            value = message.getStringProperty("nodes");
            if (StringUtils.isNotBlank(value)) {
                List<Long> nodes = Arrays.stream(StringUtils.stripToEmpty(value).split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

                if (!nodes.isEmpty()) {
                    if (nodes.size() == 1) {
                        indexer.reindexSubTree(taskId, tenant, nodes.get(0), flags, addOnly, blockSize, recursive);
                    } else {
                        indexer.reindexNodes(tenant, nodes, flags, addOnly);
                    }
                }
            }
        }
    }

    @Override
    public boolean requireTenant() {
        return true;
    }

    @Override
    public boolean requireTracing(Message message) throws JMSException {
        return ObjectUtils.getAsBoolean(message.getObjectProperty("registered"), false);
    }

    private Set<String> getUUIDs(Object value) {
        if (value == null) {
            return null;
        }

        return Arrays.stream(value.toString().split(","))
            .map(StringUtils::stripToNull)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

}
