package it.doqui.libra.librabl.utils;

import it.doqui.libra.librabl.foundation.exceptions.SystemException;

import java.util.*;

public class I18NUtils {

    private I18NUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Locale getNearestLocale(Locale templateLocale, Set<Locale> options) {
        if (options.isEmpty()) {
            return null;
        } else {
            Locale lastMatchingOption;
            if (templateLocale == null) {
                Iterator<Locale> iterator = options.iterator();
                if (iterator.hasNext()) {
                    lastMatchingOption = iterator.next();
                    return lastMatchingOption;
                }
            } else if (options.contains(templateLocale)) {
                return templateLocale;
            }

            if (templateLocale == null) {
                return null;
            }

            Set<Locale> remaining = new HashSet<>(options);
            lastMatchingOption = null;
            String templateLanguage = templateLocale.getLanguage();
            if (templateLanguage != null && !templateLanguage.isEmpty()) {
                Iterator<Locale> iterator = remaining.iterator();

                label81:
                while(true) {
                    while(true) {
                        if (!iterator.hasNext()) {
                            break label81;
                        }

                        Locale option = iterator.next();
                        if (option != null && !templateLanguage.equals(option.getLanguage())) {
                            iterator.remove();
                        } else {
                            lastMatchingOption = option;
                        }
                    }
                }
            }

            if (remaining.isEmpty()) {
                return null;
            } else if (remaining.size() == 1 && lastMatchingOption != null) {
                return lastMatchingOption;
            } else {
                lastMatchingOption = null;
                String templateCountry = templateLocale.getCountry();
                Locale locale;
                Iterator<Locale> iterator;
                if (templateCountry != null && !templateCountry.isEmpty()) {
                    iterator = remaining.iterator();

                    label64:
                    while(true) {
                        do {
                            if (!iterator.hasNext()) {
                                break label64;
                            }

                            locale = iterator.next();
                        } while(locale != null && !templateCountry.equals(locale.getCountry()));

                        lastMatchingOption = locale;
                    }
                }

                if (remaining.size() == 1 && lastMatchingOption != null) {
                    return lastMatchingOption;
                } else if (lastMatchingOption != null) {
                    return lastMatchingOption;
                } else {
                    iterator = remaining.iterator();
                    if (iterator.hasNext()) {
                        locale = iterator.next();
                        return locale;
                    } else {
                        throw new SystemException("Logic should not allow code to get here.");
                    }
                }
            }
        }
    }

    public static Locale parseLocale(String localeStr) {
        if (localeStr == null) {
            return null;
        } else {
            Locale locale = Locale.getDefault();
            StringTokenizer t = new StringTokenizer(localeStr.replace("-", "_"), "_");
            int tokens = t.countTokens();
            if (tokens == 1) {
                locale = new Locale(t.nextToken());
            } else if (tokens == 2) {
                locale = new Locale(t.nextToken(), t.nextToken());
            } else if (tokens == 3) {
                locale = new Locale(t.nextToken(), t.nextToken(), t.nextToken());
            }

            return locale;
        }
    }

}
