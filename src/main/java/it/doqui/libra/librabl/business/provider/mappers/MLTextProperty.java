package it.doqui.libra.librabl.business.provider.mappers;

import it.doqui.libra.librabl.foundation.Localizable;
import it.doqui.libra.librabl.utils.I18NUtils;

import java.util.HashMap;
import java.util.Locale;

public class MLTextProperty extends HashMap<Locale,Object> implements Localizable {

    @Override
    public Object getLocalizedValue(Locale locale) {
        Locale closestLocale = I18NUtils.getNearestLocale(locale, this.keySet());
        return this.get(closestLocale);
    }
}
