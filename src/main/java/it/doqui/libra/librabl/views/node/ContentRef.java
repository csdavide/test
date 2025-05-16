package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.foundation.NodeReferenceable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentRef implements NodeReferenceable {
    private String identity;
    private String tenant;
    private String uuid;
    private String contentPropertyName;
    private String fileName;
}
