package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.views.AbstractOperationResponse;
import it.doqui.libra.librabl.views.version.VersionItem;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeOperationResponse extends AbstractOperationResponse<NodeOperation.NodeOperationType> {
    @Schema(
        type = SchemaType.OBJECT,
        description = "Object depending on the operation: if no mapping is specified, no result is returned",
        discriminatorProperty = "op",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "CREATE", schema = NodeInfoItem.class),
            @DiscriminatorMapping(value = "UPDATE", schema = NodeInfoItem.class),
            @DiscriminatorMapping(value = "UPDATE_WHERE", schema = Long.class),
            @DiscriminatorMapping(value = "REPLACE", schema = Long.class),
            @DiscriminatorMapping(value = "DELETE_WHERE", schema = Long.class),
            @DiscriminatorMapping(value = "COPY", schema = NodeInfoItem.class),
            @DiscriminatorMapping(value = "VERSION", schema = VersionItem.class)
        }
    )
    @Override
    public Object getResult() {
        return super.getResult();
    }
}
