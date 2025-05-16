package it.doqui.libra.librabl.views.association;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = LinkItem.class)
public class LinkItemRequest extends LinkItem {
    private String path;
    private boolean createIfNotExists;

    public LinkItemRequest() {
        super();
    }

    public LinkItemRequest(LinkItem item) {
        super(item);
    }

    public LinkItemRequest(LinkItemRequest item) {
        super(item);
        this.path = item.path;
        this.createIfNotExists = item.createIfNotExists;
    }

    public LinkItemRequest(AssociationItem item, String uuid) {
        super(item, uuid);
    }
}
