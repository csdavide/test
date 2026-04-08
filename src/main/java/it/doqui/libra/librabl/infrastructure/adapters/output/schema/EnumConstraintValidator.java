package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.domain.policy.PropertyConstraintValidator;
import it.doqui.libra.librabl.domain.model.schema.ConstraintDescriptor;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumConstraintValidator implements PropertyConstraintValidator {

    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
        var ignoreCase = false;
        var _ignoreCaseValue = descriptor.getParameters().get("ignoreCase");
        var _caseSensitiveValue = descriptor.getParameters().get("caseSensitive");
        if (_ignoreCaseValue != null) {
            ignoreCase = ObjectUtils.getAsBoolean(_ignoreCaseValue, false);
        } else if(_caseSensitiveValue != null) {
            ignoreCase = !ObjectUtils.getAsBoolean(_caseSensitiveValue, false);
        }

        final Set<String> values;
        var valueList = ObjectUtils.getAsStrings(descriptor.getParameters().get("values"));
        if (valueList == null || valueList.isEmpty()) {
            valueList = ObjectUtils.getAsStrings(descriptor.getParameters().get("allowedValues"));
        }

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
