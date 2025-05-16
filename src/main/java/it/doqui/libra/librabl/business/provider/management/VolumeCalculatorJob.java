package it.doqui.libra.librabl.business.provider.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.business.provider.data.dao.VolumeDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Slf4j
@Unremovable
public class VolumeCalculatorJob implements MessageHandler {

    @Inject
    VolumeDAO volumeDAO;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AsyncOperationService asyncOperationService;

    @Override
    public void handleMessage(Message message) throws JMSException {

        var taskId = message.getStringProperty("taskId");
        var registered = message.getBooleanProperty("registered");
        log.info("Calculating volume within task {}", taskId);

        try {
            var volumes = volumeDAO.getVolumes();
            var webhook = message.getStringProperty("webhook");
            if (StringUtils.isNotBlank(webhook)) {
                try {
                    log.info("Posting calculated volumes to webhook {}", webhook);
                    var request = HttpRequest.newBuilder()
                        .uri(new URI(webhook))
                        .header("X-Requested-With", message.getStringProperty("key"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(volumes)))
                        .build();

                    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        var body = Optional.ofNullable(response.body()).map(b -> new String(b.getBytes(StandardCharsets.UTF_8))).orElse(null);
                        log.error("Unable to post calculated volumes to webhook {}: got status {} and message '{}'", webhook, response.statusCode(), body);
                    }
                } catch (URISyntaxException | IOException | InterruptedException e) {
                    log.error("Unable to post calculated volumes to webhook {}: {}", webhook, e.getMessage());
                }
            } // end if webhook

            if (registered) {
                asyncOperationService.completeTask(taskId, AsyncOperation.Status.SUCCESS, Map.of("result", volumes));
            }
            log.info("Async task {} completed", taskId);
        } catch (RuntimeException e) {
            log.error(String.format("Async operation %s failed: %s", taskId, e.getMessage()), e);
            if (registered) {
                asyncOperationService.completeTask(taskId, AsyncOperation.Status.FAILED, Map.of("message", e.getMessage()));
            }
        }
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}
