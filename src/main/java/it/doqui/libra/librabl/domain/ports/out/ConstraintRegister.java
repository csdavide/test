package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.domain.policy.PropertyConstraintValidator;

public interface ConstraintRegister {
    void register(String type, PropertyConstraintValidator validator);
    PropertyConstraintValidator getValidator(String type);
}
