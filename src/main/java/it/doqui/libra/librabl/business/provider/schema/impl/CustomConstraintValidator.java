package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.business.provider.schema.PropertyConstraintValidator;
import it.doqui.libra.librabl.views.schema.ConstraintDescriptor;
import it.doqui.libra.librabl.utils.ObjectUtils;

public class CustomConstraintValidator implements PropertyConstraintValidator {
    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
        String className = ObjectUtils.getAsString(descriptor.getParameters().get("class"));
        if (className == null) {
            throw new RuntimeException("Missing expression in CLASS constraint");
        }

        try {
            Class<?> clazz = Class.forName(className);
            PropertyConstraintValidator validator = (PropertyConstraintValidator) clazz.getConstructor().newInstance();
            validator.validate(descriptor, value);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
