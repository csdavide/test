package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.foundation.NodeDescriptor;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = InputNodeRequest.class)
public class LinkedInputNodeRequest extends InputNodeRequest implements NodeDescriptor {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<LinkItemRequest> associations;

    @JsonProperty("acl")
    private PermissionsDescriptor permissionsDescriptor;

    private ContentStreamRef copyStreamFrom;

    public LinkedInputNodeRequest() {
        super();
        this.associations = new LinkedList<>();
    }

    @Getter
    @Setter
    @ToString(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(allOf = ContentRef.class)
    public static class ContentStreamRef extends ContentRef {

        @JsonProperty("uri")
        private URI uri;

        @JsonProperty("renameTo")
        private ContentBasicDescriptor target;
    }

}
