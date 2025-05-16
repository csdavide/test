package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.business.provider.schema.PropertyConstraintValidator;
import it.doqui.libra.librabl.views.schema.ConstraintDescriptor;
import it.doqui.libra.librabl.utils.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexConstraintValidator implements PropertyConstraintValidator {

    @Override
    public void validate(ConstraintDescriptor descriptor, Object value) throws ConstraintException {
        String exp = ObjectUtils.getAsString(descriptor.getParameters().get("expression"));
        if (exp == null) {
            throw new RuntimeException("Missing expression in REGEX constraint");
        }

        Pattern pattern = Pattern.compile(exp);
        String s = ObjectUtils.getAsString(value);
        Matcher matcher = pattern.matcher(StringUtils.stripToEmpty(s));
        boolean match = matcher.matches();
        boolean requiresMatch = ObjectUtils.getAsBoolean(descriptor.getParameters().get("requiresMatch"), true);
        if ((!match && requiresMatch) || (match && !requiresMatch)) {
            throw new ConstraintException(
                String.format("REGEX %s '%s' match failed for value '%s'", requiresMatch ? "=" : "!=", exp, s)
            );
        }
    }
}
