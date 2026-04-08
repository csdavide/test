package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.domain.policy.CopyMode;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import it.doqui.libra.librabl.application.model.association.LinkItem;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Set;

@Getter
@Setter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeOperation extends AbstractOperation<NodeOperation.NodeOperationType> {
    private String uuid;

    @Schema(description = "Link details for association operations")
    private LinkItem association;

    @Schema(description = "List of options to alter the behaviour of the operation. The option set depends on the operation")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Set<OperationOption> options = Set.of();

    @Schema(
        type = SchemaType.OBJECT,
        description = "Object depending on the operation: if no mapping is specified, no operand is required",
        discriminatorProperty = "op",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "CREATE", schema = LinkedInputNodeRequest.class),
            @DiscriminatorMapping(value = "UPDATE", schema = InputNodeRequest.class),
            @DiscriminatorMapping(value = "CREATE_OR_UPDATE", schema = LinkedInputNodeRequest.class),
            @DiscriminatorMapping(value = "UPDATE_WHERE", schema = ConditionalUpdateRequest.class),
            @DiscriminatorMapping(value = "VERSION", schema = String.class),
            @DiscriminatorMapping(value = "REPLACE", schema = ReplaceOperand.class),
            @DiscriminatorMapping(value = "RENAME", schema = RenameOperand.class),
            @DiscriminatorMapping(value = "RESTORE", schema = RestoreOperand.class),
            @DiscriminatorMapping(value = "COPY", schema = CopyOperand.class),
            @DiscriminatorMapping(value = "MOVE", schema = String.class),
            @DiscriminatorMapping(value = "DELETE", schema = DeleteOperand.class),
            @DiscriminatorMapping(value = "DELETE_WHERE", schema = ConditionalDeleteRequest.class)
        }
    )
    @Override
    public Object getOperand() {
        return super.getOperand();
    }

    public enum NodeOperationType {
        CREATE(true, 1),
        UPDATE(true, 1),
        UPDATE_WHERE(false, 0),
        VERSION(false, 0),
        REPLACE(false, 0),
        COPY(false, 0),
        LINK(false, 0),
        UNLINK(false, 0),
        MOVE(false, 0),
        RENAME(true, 1),
        RESTORE(false, 0),
        DELETE(true, 2),
        DELETE_WHERE(false, 0),
        CREATE_OR_UPDATE(true, 1);

        private final boolean multiple;

        @Getter
        private final int compatibilityGroup;

        NodeOperationType(boolean multiple, int compatibilityGroup) {
            this.multiple = multiple;
            this.compatibilityGroup = compatibilityGroup;
        }

        public boolean canSupportMultiple() {
            return multiple;
        }
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class RenameOperand {
        private LinkMode mode;
        private String propertyName;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @Setter
    @Accessors(chain = true)
    public static class RestoreOperand {
        private LinkMode mode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(chain = true)
    @ToString
    public static class CopyOperand {
        private boolean copyChildren;
        private boolean excludeAssociations;
        private CopyMode copyMode;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class DeleteOperand {
        private DeleteMode mode;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class ReplaceOperand {
        private String uuid;
        private Integer version;
    }

}
