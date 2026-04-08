package it.doqui.libra.librabl.application.support;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class PathUtils {

    private PathUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }

        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).collect(Collectors.joining("/","/", "/"));
    }

}
