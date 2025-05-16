package it.doqui.libra.librabl.views.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.views.AbstractOperation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.*;

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
            @DiscriminatorMapping(value = "REINDEX", schema = ReindexOperand.class),
            @DiscriminatorMapping(value = "SENDEVENT", schema = SendEventOperand.class),
            @DiscriminatorMapping(value = "NODECLEAN", schema = NodeCleanOperand.class),
            @DiscriminatorMapping(value = "CALCVOLUME", schema = CalcVolumeOperand.class),
            @DiscriminatorMapping(value = "SOLRSYNC", schema = String.class)
        }
    )
    @Override
    public Object getOperand() {
        return super.getOperand();
    }

    private long delay;

    public enum MgmtOperationType {
        SENDEVENT,
        REINDEX,
        SOLRSYNC,
        TXCLEAN,
        NODECLEAN,
        CALCVOLUME,
        UPDATEMODELS
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReindexOperand {
        private Collection<Long> nodes;
        private Collection<Long> transactions;
        private ZonedDateTime from;
        private ZonedDateTime to;
        private String flags;
        private int blockSize;
        private boolean addOnly;
        private boolean recursive;
        private int priority;
    }

    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendEventOperand {
        private String event;

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        private final Map<String,Object> properties;

        public SendEventOperand() {
            properties = new HashMap<>();
        }
    }

    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeCleanOperand {
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        private final List<String> uuids;

        public NodeCleanOperand() {
            this.uuids = new ArrayList<>();
        }
    }

    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalcVolumeOperand {
        private String webhook;
        private String key;
    }
}
