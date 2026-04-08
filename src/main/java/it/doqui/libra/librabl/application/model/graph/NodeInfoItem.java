package it.doqui.libra.librabl.application.model.graph;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Getter
@Builder
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = Identifier.class)
public class NodeInfoItem implements Identifier {
    private Long id;
    private String tenant;
    private String uuid;
    private String typeName;
    private String code;
}
