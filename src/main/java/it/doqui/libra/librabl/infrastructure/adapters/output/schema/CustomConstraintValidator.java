package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.domain.policy.PropertyConstraintValidator;
import it.doqui.libra.librabl.domain.model.schema.ConstraintDescriptor;
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
