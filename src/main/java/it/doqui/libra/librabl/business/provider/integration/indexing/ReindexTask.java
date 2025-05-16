package it.doqui.libra.librabl.business.provider.integration.indexing;

import it.doqui.libra.librabl.business.provider.integration.messaging.MessagingTask;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.messaging.MessageType.REINDEX;

@Getter
@Setter
@ToString
public class ReindexTask implements MessagingTask {
    private final String messageType;
    private String taskId;
    private String tenant;
    private final List<Long> txList;
    private int flags = IndexingFlags.FULL_FLAG_MASK;
    private Set<String> includeSet;
    private Set<String> excludeSet;
    boolean completed = true;
    boolean addOnly = false;

    private Integer priority;

    @Setter
    private String queueName;

    public ReindexTask() {
        this.messageType = REINDEX;
        this.txList = new ArrayList<>();
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    public void setTx(long tx) {
        this.txList.add(tx);
    }

    @Override
    public Message createMessage(JMSContext context) throws JMSException {
        Message message = context.createMessage();
        message.setJMSType(this.getMessageType());
        message.setStringProperty("tenant", this.getTenant());
        message.setStringProperty("tx", txList.stream().map(String::valueOf).collect(Collectors.joining(",")));
        message.setStringProperty("flags", IndexingFlags.formatAsBinary(this.getFlags()));
        message.setBooleanProperty("completed", this.isCompleted());
        message.setBooleanProperty("addOnly", this.isAddOnly());

        if (this.getIncludeSet() != null) {
            message.setStringProperty("include", String.join(",", this.getIncludeSet()));
        }

        if (this.getExcludeSet() != null) {
            message.setStringProperty("exclude", String.join(",", this.getExcludeSet()));
        }

        if (this.getPriority() != null) {
            message.setJMSPriority(this.getPriority());
        }

        return message;
    }
}
