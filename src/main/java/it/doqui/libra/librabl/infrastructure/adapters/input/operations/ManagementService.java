package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.domain.model.graph.IndexingFlags;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing.IndexerDelegate;
import it.doqui.libra.librabl.infrastructure.platform.boot.BootEvent;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.infrastructure.platform.tx.TxReindexRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class ManagementService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationManagerPort authenticationService;

    @Inject
    IndexerDelegate indexerDelegate;

    @Inject
    SessionContext sessionContext;

    private BootEvent bootEvent;

    void onStart(@Observes BootEvent ev) {
        this.bootEvent = ev;
    }

    public Map<?,?> getBootAttributes() {
        return Optional.ofNullable(bootEvent).map(BootEvent::getAttributes).orElse(null);
    }

    public void performOperations(List<MgmtOperation> operations) {
        for (var operation : operations) {
            performOperation(operation);
        }
    }

    public void performOperations(String tenant, List<MgmtOperation> operations) {
        authenticationService.autenticateIfRequired(TenantRef.valueOf(tenant), true);
        for (var operation : operations) {
            performOperation(operation);
        }
    }

    private void performOperation(MgmtOperation operation) {
        if (Objects.requireNonNull(operation.getOp()) == MgmtOperation.MgmtOperationType.REINDEX) {
            if (operation.getOperand() != null) {
                var operand = objectMapper.convertValue(operation.getOperand(), MgmtOperation.ReindexOperand.class);
                if (operand.getFlags() == null) {
                    operand.setFlags(ObjectUtils.formatBinary(IndexingFlags.FULL_FLAG_MASK, 5));
                }

                if (operand.getTransactions() == null) {
                    throw new BadRequestException("No transactions specified for REINDEX operation");
                }

                var txReindexRequest = new TxReindexRequest();
                txReindexRequest.setTenant(sessionContext.getTenant());
                txReindexRequest.getTransactions().addAll(operand.getTransactions());
                txReindexRequest.setAddOnly(operand.isAddOnly());
                txReindexRequest.setPriority(operand.getPriority());
                txReindexRequest.setDelay(Duration.ofMillis(operation.getDelay()));
                indexerDelegate.submitReindex(txReindexRequest);
            } else {
                throw new BadRequestException("No operand specified for REINDEX operation");
            }
        } else {
            throw new BadRequestException("Unsupported operation " + operation.getOp());
        }
    }
}
