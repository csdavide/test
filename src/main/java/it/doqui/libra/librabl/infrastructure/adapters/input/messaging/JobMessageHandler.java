package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Unremovable
@Slf4j
public class JobMessageHandler implements MessageHandler {

    @Inject
    JobUseCase jobService;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var jobId = message.getStringProperty("taskId");
        var schema = message.getStringProperty("workspace");
        jobService.executeAsyncJob(jobId, schema);
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}
