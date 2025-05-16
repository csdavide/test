package it.doqui.libra.librabl.business.provider.async;

import it.doqui.libra.librabl.business.provider.integration.messaging.MessagingTask;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.Setter;
import lombok.ToString;

import static it.doqui.libra.librabl.business.provider.integration.messaging.MessageType.LINK;

@Setter
@ToString
public final class LinkTask implements MessagingTask {

    private String taskId;
    private AuthorityRef authorityRef;
    private String child;
    private String parent;
    private String type;
    private String name;
    private boolean hard;
    private String relationship;
    private String queueName;

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public String getMessageType() {
        return LINK;
    }

    @Override
    public Integer getPriority() {
        return null;
    }

    @Override
    public Message createMessage(JMSContext context) throws JMSException {
        var message = context.createMessage();
        message.setJMSType(this.getMessageType());
        message.setStringProperty("taskId", taskId);
        message.setStringProperty("authority", authorityRef.toString());
        message.setStringProperty("child", child);
        message.setStringProperty("parent", parent);
        message.setStringProperty("type", type);
        message.setStringProperty("name", name);
        message.setBooleanProperty("hard", hard);
        message.setStringProperty("relationship", relationship);

        return message;
    }
}
