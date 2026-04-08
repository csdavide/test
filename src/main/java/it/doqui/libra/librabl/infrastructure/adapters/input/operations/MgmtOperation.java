package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString(callSuper = true)
public class MgmtOperation extends AbstractOperation<MgmtOperation.MgmtOperationType> {

    @Schema(
        type = SchemaType.OBJECT,
        description = "Object depending on the operation: if no mapping is specified, no operand is required",
        discriminatorProperty = "op",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "REINDEX", schema = ReindexOperand.class)
        }
    )
    @Override
    public Object getOperand() {
        return super.getOperand();
    }

    private long delay;

    public enum MgmtOperationType {
        REINDEX
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReindexOperand {
        private Collection<Long> transactions;
        private String flags;
        private boolean addOnly;
        private int priority;
    }

}
