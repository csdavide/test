package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = NodeItem.class)
public class ArchivedNodeItem extends NodeItem {

    private ArchivedStatus status;

    public ArchivedNodeItem() {
        super();
    }
}
