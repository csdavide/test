package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.business.provider.schema.PropertyConstraintValidator;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.views.schema.ConstraintDescriptor;

public class AlwaysOkConstraintValidator implements PropertyConstraintValidator {
    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
    }
}
