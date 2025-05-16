package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.Association;
import it.doqui.index.ecmengine.mtom.dto.Job;
import it.doqui.index.ecmengine.mtom.dto.MtomOperationContext;
import it.doqui.index.ecmengine.mtom.dto.Node;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.interfaces.AssociationService;
import it.doqui.libra.librabl.business.service.interfaces.MultipleNodeOperationService;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.AssociationItem;
import it.doqui.libra.librabl.views.node.NodeOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static it.doqui.libra.librabl.views.node.NodeOperation.NodeOperationType.MOVE;

@ApplicationScoped
@Slf4j
public class AsyncOpServiceBridge extends AbstractServiceBridge {

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    AssociationService associationService;

    @Inject
    MultipleNodeOperationService multipleNodeOperationService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Job getServiceJobInfo(String jobName, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, ReadException {
        validate(() -> Objects.requireNonNull(jobName, "Job name is mandatory"));

        return call(context, () -> asyncOperationService
            .getTask(jobName)
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
            var moveOp = new NodeOperation();
            moveOp.setUuid(source.getUid());
            moveOp.setOp(MOVE);
            moveOp.setOperand(parent.getUid());

            if (newAssociation != null) {
                var linkItem = asLink(parent.getUid(), newAssociation, true);
                moveOp.setAssociation(linkItem);
            }

            AsyncOperation<?> f = multipleNodeOperationService.performOperations(List.of(moveOp), null, OperationMode.ASYNC);
            return map(f);
        });
    }

    private Job map(AsyncOperation<?> op) {
        var job = new Job();
        job.setName(op.getOperationId());
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
}
