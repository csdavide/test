package it.doqui.libra.librabl.views.renditions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(allOf = LinkedInputNodeRequest.class)
@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransformerIdentifiedInputRequest extends LinkedInputNodeRequest {
    private String rtUuid;
}
