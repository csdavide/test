package it.doqui.libra.librabl.business.provider.schema;

import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.views.schema.ConstraintDescriptor;

public interface PropertyConstraintValidator {
    void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException;
}
