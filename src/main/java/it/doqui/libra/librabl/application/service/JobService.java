package it.doqui.libra.librabl.application.service;

import io.quarkus.arc.All;
import it.doqui.libra.librabl.application.jobs.JobExecutor;
import it.doqui.libra.librabl.application.jobs.ReservedJob;
import it.doqui.libra.librabl.application.model.configuration.AsyncConfig;
import it.doqui.libra.librabl.application.model.events.EventType;
import it.doqui.libra.librabl.application.model.events.SendEventRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import it.doqui.libra.librabl.application.model.jobs.responses.PartiallyCompletable;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.application.ports.out.EventRepository;
import it.doqui.libra.librabl.application.ports.out.JobRepository;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@ApplicationScoped
@Slf4j
public class JobService implements JobUseCase {

    @Inject
    @All
    List<JobExecutor> executors;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    JobRepository jobRepository;

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Inject
    EventRepository eventRepository;

    @Inject
    SessionContext sessionContext;

    private final Map<String, JobExecutor> executorMap;

    public JobService() {
        this.executorMap = new HashMap<>();
    }

    @PostConstruct
    void init() {
        if (executors != null) {
            for (var executor : executors) {
                var currentJob = executorMap.putIfAbsent(executor.getHandledKind(), executor);
                if (currentJob != null) {
                    log.warn("Duplicate job executor for kind {}: job {} is already registered", executor.getHandledKind(), currentJob.getClass().getName());
                }
            }
        }
    }

    @Override
    public JobResponse getJob(String jobId, String tenant) {
        if (StringUtils.isNotBlank(tenant)) {
            authenticationManagerPort.autenticateIfRequired(TenantRef.valueOf(tenant), true);
        }

        return getJob(jobId);
    }

    @Override
    public JobResponse getJob(String jobId) {
        return jobRepository.getJob(jobId);
    }

    @Override
    public JobResponse setJob(String jobId, JobStatus status, JobResult result) {
        return jobRepository.setJob(jobId, status, result);
    }

    @Override
    public JobResponse failJob(String jobId, String message) {
        return jobRepository.failJob(jobId, message);
    }

    @Override
    public JobResponse cancelJob(String jobId, String tenant) {
        if (StringUtils.isNotBlank(tenant)) {
            authenticationManagerPort.autenticateIfRequired(TenantRef.valueOf(tenant), true);
        }

        var r = jobRepository.cancelJob(jobId);
        var abort = new SendEventRequest(EventType.JOB_ABORT);
        abort.getProperties().put("jobId", jobId);
        eventRepository.sendEvent(abort);
        return r;
    }

    @Override
    public JobResponse executeJob(@NotNull JobRequest request) {
        log.trace("Evaluating job request {}", request);
        try {
            if (request == null || request.getKind() == null) {
                throw new BadRequestException("Invalid job request: kind is mandatory");
            }

            var executor = executorMap.get(request.getKind());
            if (executor == null) {
                throw new BadRequestException("No executor defined for job kind " + request.getKind());
            }

            if (!executor.requiredRoles().isEmpty()) {
                if (executor.requiredRoles().stream().noneMatch(role -> sessionContext.getUserContext().isUserInRole(role))) {
                    throw new ForbiddenException("User does not have required role to execute job " + request.getKind());
                }
            }

            if (executor.isMasterSchemaRequired()) {
                authenticationManagerPort.autenticateIfRequired(TenantRef.valueOf(TenantRef.DEFAULT_TENANT), true);
            }

            if (sessionContext.getMode() == SessionMode.SYNC && request.getMode() != OperationMode.SYNC) {
                return submitAsyncJobExecution(executor, request);
            }

            return executeJobImmediately(executor, request);
        } catch (RuntimeException e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    @Override
    public void resumeAsyncJob(String jobId, String schema, JobStatus status, Function<JobResult,JobResult> updater) {
        log.info("Resuming async job {} on schema {}", jobId, schema);
        final JobRequest request = lookupJob(jobId, schema);
        if (request == null) {
            throw new NotFoundException("Job not found");
        }

        jobRepository.updateJob(jobId, status, updater);
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void executeAsyncJob(String jobId, String schema) {
        log.info("Processing async job {} on schema {}", jobId, schema);
        try {
            final JobRequest request = lookupJob(jobId, schema);
            if (request == null) {
                return;
            }

            var executor = executorMap.get(request.getKind());
            if (executor == null) {
                throw new BadRequestException("No executor defined for job kind: " + request.getKind());
            }

            if (executor instanceof ReservedJob reservedJob) {
                reservedJob.decryptRequest(request);
            }

            log.trace("Executing async job {} using user {}", jobId, sessionContext.getUserContext().getAuthorityRef());
            var result = executor.executeJob(request);
            log.trace("Completing async job {} using user {}", jobId, sessionContext.getUserContext().getAuthorityRef());
            if (result instanceof PartiallyCompletable pc) {
                if (pc.isAborted()) {
                    jobRepository.setJob(jobId, JobStatus.FAILED, result);
                } else if (pc.isCompleted()) {
                    jobRepository.setJob(jobId, JobStatus.COMPLETED, result);
                } else {
                    jobRepository.setJob(jobId, JobStatus.WAITING, result);
                }
            } else {
                jobRepository.setJob(jobId, JobStatus.COMPLETED, result);
            }

        } catch (RuntimeException e) {
            log.error("Error executing async job {}", jobId, e);
            var message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            jobRepository.failJob(jobId, message);
        }
    }

    private JobRequest lookupJob(String jobId, String schema) {
        final JobRequest request;
        try {
            BiConsumer<String, Set<String>> onAuth = schema == null
                    ? null
                    : (authority, roles) -> authenticationManagerPort.authenticateUser(
                    AuthorityRef.valueOf(authority), null, roles, SessionMode.ASYNC);
            return jobRepository.takeJob(jobId, schema, onAuth);
        } catch (NotFoundException e) {
            log.warn("Job {} not found", jobId);
            return null;
        }
    }

    private JobResponse executeJobImmediately(JobExecutor executor, JobRequest request) {
        var response = new JobResponse();
        response.setCreatedAt(ZonedDateTime.now());
        try {
            log.trace("Executing job {}", request);
            var result = executor.executeJob(request);
            if (result instanceof PartiallyCompletable pc) {
                if (pc.isAborted()) {
                    response.setStatus(JobStatus.FAILED);
                } else if (pc.isCompleted()) {
                    response.setStatus(JobStatus.COMPLETED);
                } else {
                    response.setStatus(JobStatus.WAITING);
                }
            } else {
                response.setStatus(JobStatus.COMPLETED);
            }

            response.setResult(result);
        } catch (RuntimeException e) {
            response.setStatus(JobStatus.FAILED);
            var message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            response.setMessage(message);
        } finally {
            response.setUpdatedAt(ZonedDateTime.now());
        }

        return response;
    }

    private JobResponse submitAsyncJobExecution(JobExecutor executor, JobRequest request) {
        if (executor instanceof ReservedJob reservedJob) {
            reservedJob.encryptRequest(request);
        }

        final String queue;
        if (executor.isLongOperationRequired()) {
            queue = asyncConfig.longOperations().queue();
        } else if (Optional.ofNullable(request.getDelay()).orElse(Duration.ZERO).toMillis() > asyncConfig.expirables().durationGreaterThan().toMillis()) {
            queue = asyncConfig.expirables().queue();
        } else {
            queue = asyncConfig.operations().queue();
        }

        return jobRepository.createJob(request, queue);
    }
}
