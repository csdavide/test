package it.doqui.libra.librabl.domain.model.schema;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.*;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = IndexableProperty.class)
public abstract class AbstractPropertyDescriptor implements IndexableProperty {
    protected String name;
    protected String title;
    protected String type;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    protected boolean multiple;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean opaque;

    @JsonSetter(nulls = Nulls.SKIP)
    private boolean indexed = true;

    @JsonSetter(nulls = Nulls.SKIP)
    private boolean tokenized = true;

    private boolean reverseTokenized;
    private String tokenizationType;

    @Override
    public boolean isTokenized() {
        if (getType() == null) return false;

        return switch (getType()) {
            case TYPE_TEXT, TYPE_MLTEXT -> tokenized;
            default -> false;
        };
    }

    @Override
    public boolean isReverseTokenized() {
        if (getType() == null) return false;

        return switch (getType()) {
            case TYPE_TEXT, TYPE_MLTEXT -> reverseTokenized;
            default -> false;
        };
    }

    @JsonIgnore
    public boolean isNotTokenizedFieldRequired() {
        return isTokenized() && isReverseTokenized();
    }

    @JsonIgnore
    public boolean isAdditionalTokenizedFieldRequired() {
        return !isTokenized() && isReverseTokenized();
    }
}
