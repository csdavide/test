package it.doqui.libra.librabl.views.renditions;

import it.doqui.libra.librabl.utils.ObjectUtils;

import java.util.*;

public class RenditionMap {

    public static Map<String, Collection<String>> parse(Collection<String> renditionMap) {
        Map<String, Collection<String>> result = new HashMap<>();
        for (String s : renditionMap) {
            String[] uuids = s.split("=");
            ObjectUtils.addValueInCollection(result, uuids[0], uuids[1]);
        }
        return result;
    }

    public static List<String> asList(Map<String, Collection<String>> renditionMap) {
        if (renditionMap == null || renditionMap.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (var entry : renditionMap.entrySet()) {
            for (String value : entry.getValue()) {
                result.add(entry.getKey() + "=" + value);
            }
        }
        return result;
    }
}
