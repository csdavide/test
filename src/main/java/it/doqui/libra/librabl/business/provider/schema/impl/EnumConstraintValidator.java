package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.business.provider.schema.PropertyConstraintValidator;
import it.doqui.libra.librabl.views.schema.ConstraintDescriptor;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumConstraintValidator implements PropertyConstraintValidator {

    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
        var ignoreCase = ObjectUtils.getAsBoolean(descriptor.getParameters().get("ignoreCase"), false);
        final Set<String> values;

        var valueList = ObjectUtils.getAsStrings(descriptor.getParameters().get("values"));
        if (ignoreCase) {
            values = valueList.stream().map(StringUtils::lowerCase).collect(Collectors.toSet());
        } else {
            values = new HashSet<>(valueList);
        }

        var s = ObjectUtils.getAsString(value);
        if (!values.contains(ignoreCase ? s.toLowerCase() : s)) {
            throw new ConstraintException(
                String.format("ENUM (%s) match failed for value '%s'", values, s)
            );
        }
    }
}
