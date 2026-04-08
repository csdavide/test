package it.doqui.libra.librabl.application.model.properties;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import it.doqui.libra.librabl.foundation.Localizable;
import it.doqui.libra.librabl.utils.I18NUtils;

import java.util.HashMap;
import java.util.Locale;

@JsonDeserialize(keyUsing = MLTextProperty.LocaleKeyDeserializer.class)
public final class MLTextProperty extends HashMap<Locale,Object> implements Localizable, PropertyObject {

    @Override
    public Object getLocalizedValue(Locale locale) {
        Locale closestLocale = I18NUtils.getNearestLocale(locale, this.keySet());
        return this.get(closestLocale);
    }

    public static class LocaleKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctx) {
            return I18NUtils.parseLocale(key);
        }
    }
}
