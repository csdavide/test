package it.doqui.libra.librabl.business.provider.management;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.boot.BootEvent;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.provider.integration.messaging.MessageType;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.provider.security.AuthenticationManager;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ManagementService;
import it.doqui.libra.librabl.business.service.interfaces.ReindexService;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.management.MgmtOperation;
import it.doqui.libra.librabl.business.provider.data.dao.VolumeDAO;
import it.doqui.libra.librabl.views.management.VolumeInfo;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.schema.impl.TenantSchema.COMMON_SCHEMA;

@ApplicationScoped
@Slf4j
public class ManagementServiceImpl implements ManagementService {

    @ConfigProperty(name = "libra.reindex.queue", defaultValue = "tasks")
    String reindexQueueName;

    @Inject
    TaskProducer producer;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ReindexService reindexService;

    @Inject
    VolumeDAO volumeDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    AuthenticationManager authenticationService;

    @Inject
    ModelManager modelManager;

    @Inject
    TenantDataManager tenantDataManager;

    private BootEvent bootEvent;

    void onStart(@Observes BootEvent ev) {
        this.bootEvent = ev;
    }

    @Override
    public Map<?,?> getBootAttributes() {
        return Optional.ofNullable(bootEvent).map(BootEvent::getAttributes).orElse(null);
    }

    @Override
    public Collection<VolumeInfo> getVolumes() {
        return volumeDAO.getVolumes();
    }

    @Override
    public AsyncOperation<Void> submitNodeReindex(String tenant, String node, boolean recursive, int priority, int blockSize) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(tenant), true);

        final long nodeId;
        if (StringUtils.isNumeric(node)) {
            nodeId = Long.parseLong(node);
        } else {
            nodeId = nodeDAO.findNodeByUUID(node)
                .map(ActiveNode::getId)
                .orElseThrow(() -> new NotFoundException("Unable to find node " + node));
        }

        var queue = queueForReindex(priority);
        return asyncOperationService.submit(MessageType.REINDEX, message -> {
            try {
                if (priority > 0) {
                    message.setJMSPriority(priority);
                }

                if (blockSize > 0) {
                    message.setIntProperty("blockSize", blockSize);
                }

                message.setBooleanProperty("complete", true);
                message.setBooleanProperty("recursive", recursive);
                message.setStringProperty("nodes", String.valueOf(nodeId));

            } catch (JMSException e) {
                throw new SystemException(e);
            }
        }, queue, 0, true, Map.of("node", nodeId, "recursive", recursive));
    }

    @Override
    public AsyncOperation<Void> submitTransactionsReindex(String tenant, List<Long> transactions, int priority) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(tenant), true);

        var queue = queueForReindex(priority);
        return asyncOperationService.submit(MessageType.REINDEX, message -> {
            try {
                if (priority > 0) {
                    message.setJMSPriority(priority);
                }

                message.setBooleanProperty("complete", true);
                message.setStringProperty("tx", transactions.stream().map(Object::toString).collect(Collectors.joining(",")));
            } catch (JMSException e) {
                throw new SystemException(e);
            }
        }, queue, 0, true, null);
    }

    private String queueForReindex(int priority) {
        return asyncConfig.consumers().stream()
            .filter(AsyncConfig.ConsumerConfig::isForReindex)
            .filter(c -> c.priority() <= priority)
            .sorted((a, b) -> -1 * Integer.compare(a.priority(), b.priority()))
            .map(AsyncConfig.ConsumerConfig::channel)
            .findFirst()
            .orElse(reindexQueueName);
    }

    @Override
    public AsyncOperation<Void> submitVolumesCalculation() {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
        return asyncOperationService.submit(MessageType.CALC_VOLUMES, null, null, 0, true);
    }

    @Override
    public AsyncOperation<Collection<VolumeInfo>> getCalculatedVolumes(String taskId) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
        return asyncOperationService.getTask(taskId)
            .filter(t -> StringUtils.equals(ObjectUtils.getAsString(t.getData().get("type")), MessageType.CALC_VOLUMES))
            .map(t -> AsyncOperationImpl.builder()
                .operationId(t.getOperationId())
                .status(t.getStatus())
                .message(t.getMessage())
                .result(asVolumes(t.getData().get("result")))
                .build())
            .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    @Override
    public void deleteCalculatedVolumes(String taskId) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
        asyncOperationService.removeTask(taskId, t -> StringUtils.equals(ObjectUtils.getAsString(t.getData().get("type")), MessageType.CALC_VOLUMES));
    }

    private Collection<VolumeInfo> asVolumes(Object obj) {
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }

    @Getter
    @Builder
    private static class AsyncOperationImpl implements AsyncOperation<Collection<VolumeInfo>> {

        private String operationId;
        private Status status;
        private Collection<VolumeInfo> result;
        private String message;

        @Override
        public boolean isDone() {
            return status == Status.SUCCESS || status == Status.FAILED;
        }

        @Override
        public Collection<VolumeInfo> get() {
            return result;
        }
    }

    @Override
    public void performOperations(List<MgmtOperation> operations) {
        for (var operation : operations) {
            performOperation(operation);
        }
    }

    @Override
    public void performOperations(String tenant, List<MgmtOperation> operations) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(tenant), true);
        for (var operation : operations) {
            performOperation(operation);
        }
    }

    @Override
    public Optional<FeedbackAsyncOperation> getTask(String tenant, String taskId) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(tenant), true);
        return asyncOperationService.getTask(taskId);
    }

    private void performOperation(MgmtOperation operation) {
        switch (operation.getOp()) {
            case REINDEX: {
                if (operation.getOperand() != null) {
                    log.debug("Performing operation {} with operand {}", operation.getOp(), operation.getOperand().getClass().getSimpleName());
                    var tenantRef = UserContextManager.getContext().getTenantRef();
                    if (operation.getOperand() instanceof Number n) {
                        reindexService.reindex(tenantRef, n.longValue(), ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                    } else if (operation.getOperand() instanceof String s) {
                        reindexService.reindex(tenantRef, Long.parseLong(s), ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                    } else if (operation.getOperand() instanceof Collection<?> collection) {
                        var transactions = collection.stream().map(Object::toString).toList();
                        reindexService.reindex(tenantRef, transactions, ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                    } else {
                        var operand = objectMapper.convertValue(operation.getOperand(), MgmtOperation.ReindexOperand.class);
                        if (operand.getFlags() == null) {
                            operand.setFlags(ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                        }

                        if (operand.getNodes() != null || operand.getTransactions() != null) {
                            var queue = asyncConfig.consumers().stream()
                                .filter(AsyncConfig.ConsumerConfig::isForReindex)
                                .sorted((a, b) -> -1 * Integer.compare(a.priority(), b.priority()))
                                .map(AsyncConfig.ConsumerConfig::channel)
                                .findFirst()
                                .orElse(reindexQueueName);

                            producer.submit(context -> {
                                var message = context.createMessage();
                                message.setJMSType(MessageType.REINDEX);
                                if (operand.getPriority() > 0) {
                                    message.setJMSPriority(operand.getPriority());
                                }

                                message.setStringProperty("tenant", tenantRef.toString());
                                message.setStringProperty("flags", operand.getFlags());
                                message.setBooleanProperty("completed", true);
                                message.setBooleanProperty("addOnly", operand.isAddOnly());
                                message.setBooleanProperty("recursive", operand.isRecursive());

                                if (operand.getBlockSize() > 0) {
                                    message.setIntProperty("blockSize", operand.getBlockSize());
                                }

                                final Collection<Long> list;
                                final String name;
                                if (operand.getNodes() != null) {
                                    name = "nodes";
                                    list = operand.getNodes();
                                } else {
                                    name = "tx";
                                    list = operand.getTransactions();
                                }
                                message.setStringProperty(name, list.stream().map(String::valueOf).collect(Collectors.joining(",")));

                                if (operation.getDelay() > 0) {
                                    message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + operation.getDelay());
                                }
                                return message;
                            }, queue);
                        } else {
                            producer.submit(context -> {
                                try {
                                    var message = context.createMessage();
                                    message.setJMSType(MessageType.REINDEX_RANGE);
                                    message.setStringProperty("tenant", tenantRef.toString());
                                    message.setStringProperty("operand", objectMapper.writeValueAsString(operand));

                                    if (operation.getDelay() > 0) {
                                        message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + operation.getDelay());
                                    }
                                    return message;
                                } catch (RuntimeException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }, asyncConfig.operations().queue());
                        }
                    }
                } else {
                    throw new BadRequestException("No operand specified for REINDEX operation");
                }

                break;
            }

            case SENDEVENT: {
                var operand = objectMapper.convertValue(operation.getOperand(), MgmtOperation.SendEventOperand.class);
                producer.submit(context -> {
                        var message = context.createMapMessage();
                        message.setJMSType(MessageType.DISTRIBUTED_EVENT);
                        message.setStringProperty("event", operand.getEvent());

                        for (var entry : operand.getProperties().entrySet()) {
                            var k = entry.getKey();
                            var v = entry.getValue();

                            if (v instanceof Boolean b) {
                                message.setBooleanProperty(k, b);
                            } else if (v instanceof Number n) {
                                message.setLongProperty(k, n.longValue());
                            } else {
                                message.setStringProperty(k, v.toString());
                            }
                        }

                        if (operation.getDelay() > 0) {
                            message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + operation.getDelay());
                        }

                        return message;
                    },
                    "topic:" + asyncConfig.producer().eventsTopic()
                );
                break;
            }

            case SOLRSYNC: {
                var tenant = Optional.ofNullable(operation.getOperand()).map(Object::toString).orElse(UserContextManager.getTenant());
                if (tenant.equals(COMMON_SCHEMA) || tenant.equals("*")) {
                    tenantDataManager.findAll().stream().map(TenantSpace::getTenant).forEach(t -> synchronizeTenant(t, operation.getDelay()));
                } else {
                    synchronizeTenant(tenant, operation.getDelay());
                }
                break;
            }

            case TXCLEAN: {
                producer.submit(context -> {
                    try {
                        var message = context.createMessage();
                        message.setJMSType(MessageType.TX_CLEAN);

                        if (operation.getDelay() > 0) {
                            message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + operation.getDelay());
                        }
                        return message;
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }, asyncConfig.operations().queue());
                break;
            }

            case NODECLEAN: {
                var tenant = UserContextManager.getTenant();
                var operand = objectMapper.convertValue(operation.getOperand(), MgmtOperation.NodeCleanOperand.class);
                producer.submit(context -> {
                    try {
                        var message = context.createMessage();
                        message.setJMSType(MessageType.NODES_CLEAN);
                        message.setStringProperty("tenant", tenant);
                        message.setStringProperty("uuids", operand.getUuids().stream().map(String::valueOf).collect(Collectors.joining(",")));

                        if (operation.getDelay() > 0) {
                            message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + operation.getDelay());
                        }
                        return message;
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }, asyncConfig.operations().queue());
                break;
            }

            case CALCVOLUME: {
                var operand = objectMapper.convertValue(operation.getOperand(), MgmtOperation.CalcVolumeOperand.class);
                if (StringUtils.isBlank(operand.getWebhook())) {
                    throw new BadRequestException("Invalid webhook in calculation volume operation");
                }

                asyncOperationService.submit(MessageType.CALC_VOLUMES, message -> {
                    try {
                        message.setStringProperty("webhook", operand.getWebhook());
                        message.setStringProperty("key", operand.getKey());
                    } catch (JMSException e) {
                        throw new SystemException(e);
                    }
                }, null, operation.getDelay(), false);
                break;
            }

            case UPDATEMODELS: {
                tenantDataManager.updateCommonModelsIfRequired();
                modelManager.sendReloadTenant(COMMON_SCHEMA, true);
                break;
            }

            default:
                throw new BadRequestException("Unsupported operation " + operation.getOp());
        }
    }

    private void synchronizeTenant(String tenant, long delay) {
        producer.submit(context -> {
            try {
                var message = context.createMessage();
                message.setJMSType(MessageType.SOLR_SYNC);
                message.setStringProperty("tenant", tenant);

                if (delay > 0) {
                    message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + delay);
                }
                return message;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }, asyncConfig.operations().queue());
    }
}
