package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.doqui.libra.librabl.views.AbstractOperation;
import it.doqui.libra.librabl.views.association.LinkItem;
import it.doqui.libra.librabl.views.association.LinkMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeOperation extends AbstractOperation<NodeOperation.NodeOperationType> {
    private String uuid;

    @Schema(description = "Link details for association operations")
    private LinkItem association;

    @Schema(description = "List of options to alter the behaviour of the operation. The option set depends on the operation")
    List<String> options;

    @Schema(
        type = SchemaType.OBJECT,
        description = "Object depending on the operation: if no mapping is specified, no operand is required",
        discriminatorProperty = "op",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "CREATE", schema = LinkedInputNodeRequest.class),
            @DiscriminatorMapping(value = "UPDATE", schema = InputNodeRequest.class),
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
        REINDEX,
        CREATE,
        UPDATE,
        UPDATE_WHERE,
        VERSION,
        REPLACE,
        COPY,
        LINK,
        UNLINK,
        MOVE,
        RENAME,
        RESTORE,
        DELETE,
        DELETE_WHERE
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
