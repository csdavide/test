package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.views.Identifier;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = InputNodeRequest.class)
public class InputIdentifiedNodeRequest extends InputNodeRequest implements Identifier {

    private String uuid;

    public void copy(InputIdentifiedNodeRequest x) {
        super.copy(x);
        this.uuid = x.uuid;
    }
}
