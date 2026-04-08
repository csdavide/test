package it.doqui.libra.librabl.domain.policy;

import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.domain.model.schema.ConstraintDescriptor;

public interface PropertyConstraintValidator {
    void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException;
}
