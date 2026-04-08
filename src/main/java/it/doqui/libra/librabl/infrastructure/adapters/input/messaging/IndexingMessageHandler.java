package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing.Indexer;
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

import static it.doqui.libra.librabl.domain.model.graph.IndexingFlags.parse;

@ApplicationScoped
@Unremovable
@Slf4j
public class IndexingMessageHandler implements MessageHandler {

    @Inject
    Indexer indexer;

    @Inject
    SessionContext sessionContext;

    @Override
    public void handleMessage(Message message) throws JMSException {
        String tenant = sessionContext.getUserContext().getTenantRef().toString();
        final int flags = parse(Objects.requireNonNullElse(message.getStringProperty("flags"), "1111"));
        boolean addOnly = ObjectUtils.getAsBoolean(message.getObjectProperty("addOnly"), false);

        var value = message.getStringProperty("tx");
        if (StringUtils.isNotBlank(value)) {
            List<Long> tx = Arrays.stream(StringUtils.stripToEmpty(value).split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

            indexer.reindexTransactions(tenant, null, tx, flags, getUUIDs(message.getObjectProperty("include")), getUUIDs(message.getObjectProperty("exclude")), true, addOnly);
        }
    }

    @Override
    public boolean requireTenant() {
        return true;
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
