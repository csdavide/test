package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = AbstractPropertyDescriptor.class)
public class PropertyDescriptor extends AbstractPropertyDescriptor implements IndexableProperty {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean hidden;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean managed;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean stored;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean immutable;
    private String defaultValue;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean declarationRequired;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("constraint-refs")
    private final List<String> constraints;

    public PropertyDescriptor() {
        this.constraints = new LinkedList<>();
    }
}
