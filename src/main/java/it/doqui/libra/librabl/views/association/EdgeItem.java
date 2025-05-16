package it.doqui.libra.librabl.views.association;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(discriminatorProperty = "_")
public class EdgeItem {

    @JsonProperty("type")
    private String typeName;
    private String name;

    @JsonSetter(nulls = Nulls.SKIP)
    private boolean hard = true;

    @JsonProperty("uuid")
    private String vertexUUID;

    public EdgeItem() {
        super();
    }

    public EdgeItem(LinkItem item) {
        super();
        this.setTypeName(item.getTypeName());
        this.setName(item.getName());
        this.setHard(item.isHard());
        this.setVertexUUID(item.getVertexUUID());
    }
}
