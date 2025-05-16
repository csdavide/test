package it.doqui.libra.librabl.views.node;

import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum MapOption {
    DEFAULT,
    SYS_PROPERTIES,
    PARENT_ASSOCIATIONS,
    PARENT_HARD_ASSOCIATIONS,
    PATHS,
    SG,
    ACL,
    TX,
    NO_NULL_PROPERTIES,
    NO_PROPERTIES,
    VARRAY,
    CHECK_ARCHIVE,
    LEGACY;

    public static Set<MapOption> valueOf(List<String> options) {
        final Set<MapOption> optionSet = new HashSet<>();
        if (options != null) {
            for (String s : options) {
                try {
                    Arrays.stream(s.split(",")).forEach(x -> {
                        optionSet.add(MapOption.valueOf(StringUtils.stripToEmpty(x).toUpperCase()));
                    });
                } catch (Exception e) {
                    throw new BadDataException("Invalid options");
                }
            }
        }
        return optionSet;
    }
}
