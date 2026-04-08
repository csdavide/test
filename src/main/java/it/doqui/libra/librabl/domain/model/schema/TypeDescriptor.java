package it.doqui.libra.librabl.domain.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = TypedInterfaceDescriptor.class)
public class TypeDescriptor extends TypedInterfaceDescriptor {

    @JsonProperty("name-padding")
    private String namePadding;

    @Override
    public <T extends TypedInterfaceDescriptor> T copyTo(Class<T> clazz) {
        T descriptor = super.copyTo(clazz);
        if (descriptor instanceof TypeDescriptor t) {
            t.setNamePadding(this.namePadding);
        }
        return descriptor;
    }
}
