package it.doqui.libra.librabl.business.provider.integration.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import it.doqui.libra.librabl.business.provider.integration.messaging.MessageType;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.EventType;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.SystemCheckEvent;
import it.doqui.libra.librabl.business.provider.integration.solr.SolrManager;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.IntegrityService;
import it.doqui.libra.librabl.views.management.SystemStatusInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class IntegrityServiceImpl implements IntegrityService {

    @ConfigProperty(name = "libra.expected-instances", defaultValue = "0")
    int installedExpectedInstances;

    @Inject
    TenantDataManager tenantManager;

    @Inject
    SolrManager solrManager;

    @Inject
    TaskProducer producer;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, BlockingQueue<SystemStatusInfo.InstanceInfo>> requestMap = new ConcurrentHashMap<>();

    void onStart(@Observes SystemCheckEvent ev) throws InterruptedException {
        log.info("Got system check event: {}", ev);
        if (StringUtils.isNotBlank(ev.getId())) {
            if (StringUtils.isBlank(ev.getCorrelationId())) {
                // got request
                var info = performLocalCheck();
                sendCheckMessage(UUID.randomUUID().toString(), ev.getId(), objectMapper.convertValue(info, Map.class));
            } else {
                // got response
                var queue = requestMap.get(ev.getCorrelationId());
                if (queue != null) {
                    if (ev.getData() != null) {
                        var info = objectMapper.convertValue(ev.getData(), SystemStatusInfo.InstanceInfo.class);
                        queue.put(info);
                    }
                }
            }
        } else {
            log.warn("No id present in system check event");
        }
    }

    @Override
    public SystemStatusInfo checkSystemStatus(int expectedInstances, long timeout) throws InterruptedException {
        if (expectedInstances == 0) {
            expectedInstances = installedExpectedInstances;
        }

        long stopTime = System.currentTimeMillis() + timeout;
        var id = UUID.randomUUID().toString();
        var queue = new ArrayBlockingQueue<SystemStatusInfo.InstanceInfo>(10);
        requestMap.put(id, queue);
        var ssi = new SystemStatusInfo();
        ssi.setExpectedCount(expectedInstances);
        ssi.setOk(true);

        try {
            sendCheckMessage(id, null, null);
            ssi.setArtemisReachable(true);
        } catch (Throwable e) {
            ssi.setOk(false);
        }

        long remainingTime;
        while ((remainingTime = stopTime - System.currentTimeMillis()) > 0 && (expectedInstances <= 0 || ssi.getInstances().size() < expectedInstances)) {
            var info = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
            if (info != null) {
                ssi.getInstances().add(info);
            }
        }

        if (expectedInstances > 0 && ssi.getInstances().size() < expectedInstances) {
            ssi.setOk(false);
        }

        if (ssi.isOk()) {
            for (var instance : ssi.getInstances()) {
                if (!instance.isOk()) {
                    ssi.setOk(false);
                    break;
                }
            }
        }

        ssi.setFeedbacks(ssi.getInstances().size());
        requestMap.remove(id);
        return ssi;
    }

    private SystemStatusInfo.InstanceInfo performLocalCheck() {
        var instanceInfo = new SystemStatusInfo.InstanceInfo();
        instanceInfo.setName(TransactionService.current().getInstanceId());
        instanceInfo.setOk(true);

        var runtime = Runtime.getRuntime();
        instanceInfo.setHeapSize(runtime.totalMemory());
        instanceInfo.setHeapMaxSize(runtime.maxMemory());
        instanceInfo.setHeapFreeSize(runtime.freeMemory());

        try {
            tenantManager.findAll();
            instanceInfo.setDatabaseReachable(true);
        } catch (Throwable e) {
            instanceInfo.setOk(false);
        }

        try {
            solrManager.findAllCollections();
            instanceInfo.setSolrReachable(true);
        } catch (Throwable e) {
            instanceInfo.setOk(false);
        }

        return instanceInfo;
    }

    private void sendCheckMessage(String id, String correlationId, Map<?,?> map) {
        producer.submit(context -> {
                var message = context.createMapMessage();
                message.setJMSType(MessageType.DISTRIBUTED_EVENT);
                message.setStringProperty("event", EventType.SYSTEM_CHECK);
                message.setStringProperty("id", id);
                message.setStringProperty("correlationId", correlationId);

                if (map != null) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        message.setObject(entry.getKey().toString(), entry.getValue());
                    }
                }

                return message;
            },
            "topic:" + asyncConfig.producer().eventsTopic()
        );
    }
}
