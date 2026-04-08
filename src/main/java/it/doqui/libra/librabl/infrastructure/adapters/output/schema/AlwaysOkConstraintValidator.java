package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.domain.policy.PropertyConstraintValidator;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.domain.model.schema.ConstraintDescriptor;

public class AlwaysOkConstraintValidator implements PropertyConstraintValidator {
    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
    }
}
