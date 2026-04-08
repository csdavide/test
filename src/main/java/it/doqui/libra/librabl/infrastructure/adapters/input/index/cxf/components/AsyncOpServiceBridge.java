package it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components;

import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.index.ecmengine.mtom.dto.Association;
import it.doqui.index.ecmengine.mtom.dto.Job;
import it.doqui.index.ecmengine.mtom.dto.MtomOperationContext;
import it.doqui.index.ecmengine.mtom.dto.Node;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.application.ports.in.AssociationUseCase;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.application.model.association.AssociationItem;
import it.doqui.libra.librabl.domain.model.graph.BindingType;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.application.model.jobs.requests.MoveJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@IfBuildProperty(name = "libra.module.cxf.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class AsyncOpServiceBridge extends AbstractServiceBridge {

    @Inject
    JobUseCase jobService;

    @Inject
    AssociationUseCase associationService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Job getServiceJobInfo(String jobName, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, ReadException {
        validate(() -> Objects.requireNonNull(jobName, "Job name is mandatory"));

        return call(context, () -> Optional.ofNullable(jobService.getJob(jobName))
            .map(this::map)
            .orElseThrow(() -> new BadRequestException("Unknown job " + jobName)));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Job linkContentJob(Node source, Node destination, Association association, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(source, "source");
        validate(destination, "destination");
        validate(() -> Objects.requireNonNull(association, "Association must be specified"));

        return call(context, () -> {
            var uuid = destination.getUid();
            var link = asLink(source.getUid(), association, false);
            AsyncOperation<AssociationItem> f = associationService.linkNode(uuid, link, OperationMode.ASYNC);
            return map(f);
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public Job moveNodeJob(Node source, Node parent, Association newAssociation, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(source, "source");
        validate(parent, "parent");

        return call(context, () -> {
            final ParentLink destination = new ParentLink();
            destination.setParent(new Vertex(VertexType.UUID, parent.getUid()));
            if (newAssociation != null) {
                destination.setType(newAssociation.getTypePrefixedName());
                destination.setName(newAssociation.getPrefixedName());
                destination.setBindingType(BindingType.HARD);
            }

            var moveJobRequest = new MoveJobRequest();
            moveJobRequest.setNode(new Vertex(VertexType.UUID, source.getUid()));
            moveJobRequest.setDestination(destination);
            moveJobRequest.setMode(OperationMode.ASYNC);
            return map(jobService.executeJob(moveJobRequest));
        });
    }

    private Job map(AsyncOperation<?> op) {
        var job = new Job();
        job.setName(op.getJobId());
        job.setMessage(op.getMessage());

        if (op.getCreatedAt() != null) {
            job.setCreated(Date.from(op.getCreatedAt().toInstant()));
        }

        if (op.getUpdatedAt() != null) {
            job.setUpdated(Date.from(op.getUpdatedAt().toInstant()));
        }

        switch (op.getStatus()) {
            case SUBMITTED:
                job.setStatus(Job.STATUS_READY);
                break;

            case RUNNING:
                job.setStatus(Job.STATUS_RUNNING);
                break;

            case SUCCESS:
                job.setStatus(Job.STATUS_FINISHED);
                break;

            case FAILED:
                job.setStatus(Job.STATUS_ERROR);
                break;
        }

        return job;
    }

    private Job map(JobResponse jobResponse) {
        var job = new Job();
        job.setName(jobResponse.getJobId());
        job.setMessage(jobResponse.getMessage());

        if (jobResponse.getCreatedAt() != null) {
            job.setCreated(Date.from(jobResponse.getCreatedAt().toInstant()));
        }

        if (jobResponse.getUpdatedAt() != null) {
            job.setUpdated(Date.from(jobResponse.getUpdatedAt().toInstant()));
        }

        job.setStatus(switch(jobResponse.getStatus()) {
            case SUBMITTED, PENDING -> Job.STATUS_READY;
            case RUNNING, WAITING -> Job.STATUS_RUNNING;
            case SUCCESS, COMPLETED -> Job.STATUS_FINISHED;
            case FAILED, CANCELLED -> Job.STATUS_ERROR;
        });

        return job;
    }
}
