package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = AbstractPropertyDescriptor.class)
public class DynamicPropertyDescriptor extends AbstractPropertyDescriptor implements IndexableProperty {
    private boolean predefined;
}
