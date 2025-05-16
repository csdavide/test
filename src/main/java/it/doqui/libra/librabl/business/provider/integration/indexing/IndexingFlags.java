package it.doqui.libra.librabl.business.provider.integration.indexing;

import it.doqui.libra.librabl.utils.ObjectUtils;

public class IndexingFlags {
    public static final int DEFAULT_FLAG_MASK = 0b0000;
    public static final int FULL_FLAG_MASK = 0b1111;
    public static final int METADATA_FLAG = 0b0001;
    public static final int TEXT_FLAG = 0b0010;
    public static final int PATH_FLAG = 0b0100;
    public static final int SG_FLAG = 0b1000;

    public static final int FORCE_FLAG = 0b10000;

    public static int combine(int... v) {
        int r = DEFAULT_FLAG_MASK;
        for (int x : v) {
            r |= x;
        }
        return r;
    }

    public static int parse(String s) {
        try {
            if (s != null) {
                return Integer.valueOf(s, 2);
            }
        } catch (Throwable e) {
            // ignore
        }

        return DEFAULT_FLAG_MASK;
    }

    public static String formatAsBinary(int flags) {
        return ObjectUtils.formatBinary(flags, 4);
    }

    public static boolean match(int flags, int m) {
        return (flags & m) == m;
    }
}
