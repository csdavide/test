package it.doqui.libra.librabl.infrastructure.adapters.output.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.doqui.libra.librabl.application.model.messaging.MessageType;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import it.doqui.libra.librabl.infrastructure.platform.telemetry.TraceEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
@Slf4j
public class Tracer {

    @ConfigProperty(name = "quarkus.log.handler.file.telemetry.enable", defaultValue = "false")
    boolean logEnabled;

    @ConfigProperty(name = "libra.telemetry.messaging.queue")
    Optional<String> traceQueueName;

    @Inject
    MessageSenderPort producer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EventLogger eventLogger;

    @Inject
    MeterRegistry registry;

    private final ExecutorService executorService = Executors
        .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("tracer-%d").build());

    void onStart(@Observes TraceEvent event) {
        executorService.submit(() -> {
            var status = switch (event.getStatus()) {
                case SUCCESS -> "SUCCESS";
                case FAILED -> "FAILURE";
                default -> null;
            };

            if (status == null) {
                return;
            }

            var type = switch (event.getCategory()) {
                case LINK, UNLINK, MOVE, MANAGEMENT -> "UPDATE";
                case RESTORE -> "CREATE";
                case GENERIC -> "READ";
                default -> event.getCategory().toString();
            };

            var tenant = event.getTenant();
            if (tenant == null && StringUtils.equals(event.getClassName(), "ManagementResource")) {
                tenant = "sys";
            }

            if (logEnabled) {
                        /* Formato output
                        %d,[%p],[%c],[%t],<elapsed>,<class.method>,<TYPE>,<USER>,<STATUS>%n
                        TYPE=CREATE|READ|UPDATE|DELETE
                        USER=ACTA.01RPGIUNTA.CORRENTE
                        STATUS=SUCCESS|FAILURE

                        %m = <elapsed>,<class.method>,<TYPE>,<USER>,<STATUS>
                         */

                var mex = String.format("%d,%s.%s,%s,%s,%s",
                    event.getDuration().toMillis(),
                    event.getClassName(),
                    event.getMethodName(),
                    type,
                    tenant,
                    status);

                log.trace(mex);
            }

            Timer
              .builder("api.timer")
              .tags("tenant", tenant, "type", type, "name", event.getClassName() + "." + event.getMethodName(), "status", status)
              .register(registry).record(event.getDuration());
						
						Counter
						  .builder("api.counter")
							.tags("tenant", tenant, "type", type)
							.register(registry).increment();

            eventLogger.log(event);
            traceQueueName.ifPresent(s -> producer.submit(context -> {
                try {
                    var json = objectMapper.writeValueAsString(event);
                    var message = context.createTextMessage(json);
                    message.setJMSType(MessageType.TRACE_EVENT);
                    return message;
                } catch (JsonProcessingException e) {
                    return null;
                }
            }, s));
        });
    }
}
