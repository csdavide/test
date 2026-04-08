package it.doqui.libra.librabl.application.model.graph;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.domain.model.files.ContentBasicDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.domain.model.graph.NodeDescriptor;
import it.doqui.libra.librabl.application.model.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.domain.model.graph.ParentLink;
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

    private ParentLink link;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(deprecated = true, description = "Use parent instead")
    @Deprecated
    private final List<LinkItemRequest> associations;

    @JsonProperty("acl")
    private PermissionsDescriptor permissionsDescriptor;

    @Schema(deprecated = true, description = "Set the corresponding property as external source instead")
    @Deprecated
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
