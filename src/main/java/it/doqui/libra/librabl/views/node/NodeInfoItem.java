package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import it.doqui.libra.librabl.views.Identifier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Getter
@AllArgsConstructor
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = Identifier.class)
public class NodeInfoItem implements Identifier {
    private Long id;
    private String tenant;
    private String uuid;
    private String typeName;
}
