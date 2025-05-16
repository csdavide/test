package it.doqui.libra.librabl.business.provider.schema;

public interface ConstraintRegister {
    void register(String type, PropertyConstraintValidator validator);
    PropertyConstraintValidator getValidator(String type);
}
