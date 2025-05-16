package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.business.provider.schema.PropertyConstraintValidator;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.schema.ConstraintDescriptor;

public class MinMaxConstraintValidator implements PropertyConstraintValidator {

    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
        var min = ObjectUtils.getAsLong(descriptor.getParameters().get("minValue"), Long.MIN_VALUE);
        var max = ObjectUtils.getAsLong(descriptor.getParameters().get("maxValue"), Long.MAX_VALUE);
        var v = ObjectUtils.getAsLong(value, 0);
        if (v < min || v > max) {
            throw new ConstraintException(String.format("MINMAX [%d,%d]) failed for value '%s'", min, max, value));
        }
    }
}
