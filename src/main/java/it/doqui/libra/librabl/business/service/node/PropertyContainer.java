package it.doqui.libra.librabl.business.service.node;

import it.doqui.libra.librabl.views.schema.PropertyDescriptor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyContainer {

    private PropertyDescriptor descriptor;
    private Object value;

    @Override
    public String toString() {
        return value == null ? null : value.toString();
    }
}
