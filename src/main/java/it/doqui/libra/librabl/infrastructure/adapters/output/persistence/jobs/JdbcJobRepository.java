package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.jobs;

import com.github.f4b6a3.uuid.UuidCreator;
import io.quarkus.narayana.jta.QuarkusTransaction;
import it.doqui.libra.librabl.application.model.events.EventType;
import it.doqui.libra.librabl.application.model.events.SendEventRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import it.doqui.libra.librabl.application.ports.out.JobRepository;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.JobDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.JobData;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.JobEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static it.doqui.libra.librabl.application.model.messaging.MessageType.JOB;

@ApplicationScoped
@Slf4j
public class JdbcJobRepository implements JobRepository {

    @Inject
    JobDAO jobDAO;

    @Inject
    SessionContext sessionContext;

    @Inject
    MessageSenderPort senderPort;

    public JobResponse createJob(JobRequest request, String queue) {
        var data = new JobData();
        data.setAuthority(sessionContext.getUserContext().getAuthorityRef().toString());
        data.setRoles(sessionContext.getUserContext().getRoleSet());
        data.setRequest(request);

        var uuid = UuidCreator.getTimeOrderedEpoch();
        var instant = Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16);
        var now = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());

        var entity = new JobEntity();
        entity.setJobId(uuid.toString());
        entity.setTenant(sessionContext.getUserContext().getTenantRef().toString());
        entity.setData(data);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setStatus(JobStatus.PENDING);
        jobDAO.persist(entity);

        long delay = Optional.ofNullable(entity.getData().getRequest().getDelay()).orElse(Duration.ZERO).toMillis();
        enqueueJob(queue, delay, 0, entity);
        return response(entity);
    }

    @Override
    public JobResponse getJob(String jobId) {
        return Optional.ofNullable(jobDAO.findById(jobId, sessionContext.getUserContext().getDbSchema(), sessionContext.getTenant()))
                .map(this::response)
                .orElseThrow(() -> new NotFoundException("Job " + jobId + " not found"));
    }

    @Override
    public JobRequest takeJob(String jobId, String schema, BiConsumer<String, Set<String>> onAuth) {
        return request(doUpdateJob(jobId, schema, null, entity -> {
            if (onAuth != null) {
                onAuth.accept(entity.getData().getAuthority(), entity.getData().getRoles());
            }
            entity.setStatus(JobStatus.RUNNING);
        }));
    }

    @Override
    public JobResponse setJob(String jobId, JobStatus status, JobResult result) {
        if (status == null) {
            throw new BadRequestException("No status specified in job " + jobId);
        }

        return response(doUpdateJob(jobId, entity -> {
            entity.setStatus(status);
            if (result != null) {
                entity.getData().setResult(result);
            }
        }));
    }

    @Override
    public JobResponse updateJob(String jobId, Function<JobResult,JobResult> updater) {
        return updateJob(jobId, null, updater);
    }

    @Override
    public JobResponse updateJob(String jobId, JobStatus status, Function<JobResult,JobResult> updater) {
        return response(doUpdateJob(jobId, entity -> {
            if (status != null) {
                entity.setStatus(status);
            }

            if (updater != null) {
                var result = updater.apply(entity.getData().getResult());
                if (result != null) {
                    entity.getData().setResult(result);
                }
            }
        }));
    }

    @Override
    public JobResponse failJob(String jobId, String message) {
        return response(doUpdateJob(jobId, entity -> {
            entity.setStatus(JobStatus.FAILED);
            entity.getData().setMessage(message);
        }));
    }

    @Override
    public JobResponse cancelJob(String jobId) {
        var r = response(doUpdateJob(jobId, entity -> entity.setStatus(JobStatus.CANCELLED)));
        var abort = new SendEventRequest(EventType.JOB_ABORT);
        abort.getProperties().put("jobId", jobId);
        return r;
    }

    @Override
    public void deleteJob(String jobId, Predicate<JobResponse> filter) {
        QuarkusTransaction.requiringNew().call(() -> {
            var entity = jobDAO.findById(jobId, sessionContext.getUserContext().getDbSchema(), sessionContext.getTenant());
            if (entity == null) {
                throw new NotFoundException("Job " + jobId + " not found");
            }

            if (!entity.getStatus().isDone()) {
                throw new PreconditionFailedException("Job " + jobId + " is not yet executed");
            }

            if (filter != null) {
                if (!filter.test(response(entity))) {
                    throw new PreconditionFailedException("Job " + jobId + " cannot be removed");
                }
            }

            if (!jobDAO.deleteById(jobId)) {
                throw new SystemException("Cannot delete job " + jobId);
            }

            return null;
        });
    }

    private JobEntity doUpdateJob(String jobId, Consumer<JobEntity> updater) {
        return doUpdateJob(jobId, sessionContext.getUserContext().getDbSchema(), sessionContext.getTenant() , updater);
    }

    private JobEntity doUpdateJob(String jobId, String schema, String tenant, Consumer<JobEntity> updater) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var entity = jobDAO.findById(jobId, schema, tenant);
            if (entity == null) {
                throw new NotFoundException("Job " + jobId + " not found");
            }

            if (entity.getStatus().isDone()) {
                if (entity.getStatus() != JobStatus.CANCELLED) {
                    log.warn("Job {} is already done, cannot be updated", jobId);
                }
            } else if (updater != null) {
                updater.accept(entity);
                jobDAO.persist(entity);
            }

            return entity;
        });
    }

    private void enqueueJob(String queue, long delay, int priority, JobEntity entity) {
        log.trace("Enqueueing job {} on queue {} with priority {}", entity.getJobId(), queue, priority);
        final var schema = sessionContext.getUserContext().getDbSchema();
        senderPort.submit(context -> {
            var message = context.createMessage();
            message.setJMSType(JOB);
            if (priority > 0) {
                message.setJMSPriority(priority);
            }

            message.setStringProperty("taskId", entity.getJobId());
            message.setStringProperty("workspace", schema);
            message.setStringProperty("authority", entity.getData().getAuthority());
            var roles = entity.getData().getRoles();
            if (roles != null && !roles.isEmpty()) {
                message.setStringProperty("roles", String.join(",", roles));
            }

            if (delay > 0) {
                message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + delay);
            }

            return message;
        }, queue);
        entity.setStatus(JobStatus.SUBMITTED);
    }

    private JobRequest request(JobEntity entity) {
        if (entity.getStatus() == null) {
            throw new PreconditionFailedException("Job " + entity.getJobId() + " has no status");
        }

        if (entity.getStatus().isDone()) {
            throw new PreconditionFailedException("Job " + entity.getJobId() + " is already done");
        }

        var data = entity.getData();
        if (data == null) {
            throw new PreconditionFailedException("Job " + entity.getJobId() + " has no data");
        }

        return data.getRequest();
    }

    private JobResponse response(JobEntity entity) {
        var response = new JobResponse();
        response.setJobId(entity.getJobId());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setResult(Optional.ofNullable(entity.getData()).map(JobData::getResult).orElse(null));
        response.setMessage(Optional.ofNullable(entity.getData()).map(JobData::getMessage).orElse(null));
        return response;
    }
}
