package it.doqui.libra.librabl.business.service.node;

import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public enum PermissionFlag {
    R(0b00001),
    W(0b00010),
    C(0b00100),
    D(0b01000),
    A(0b10000);

    private final int value;
    PermissionFlag(int value) {
        this.value = value;
    }

    public boolean match(int value) {
        return (value & this.value) == this.value;
    }

    public boolean match(String s) {
        return match(parse(s));
    }

    public static int all() {
        return 0b11111;
    }

    public static int parse(String s) {
        if (s == null) {
            return 0;
        }

        char[] chars = s.toCharArray();
        if (chars[0] == '0' || chars[0] == '1') {
            // binary parsing
            int flags = 0;
            for (int i = chars.length - 1; i >= 0; i--) {
                flags = flags << 1;
                switch (chars[i]) {
                    case '0':
                        break;
                    case '1':
                        flags |= 0b1;
                        break;
                    default:
                        throw new NumberFormatException("Invalid permission " + s);
                }
            }
            return flags;
        } else {
            Set<Character> admittedCharSet = new HashSet<>();
            admittedCharSet.add('-');
            for (PermissionFlag f : PermissionFlag.values()) {
                admittedCharSet.add(f.name().charAt(0));
            }

            int flags = 0;
            for (char aChar : chars) {
                char ch = Character.toUpperCase(aChar);
                if (!admittedCharSet.contains(ch)) {
                    throw new BadRequestException(String.format("Invalid char '%c' found in rights '%s'", aChar, s));
                }

                if (ch != '-') {
                    flags |= PermissionFlag.valueOf(String.valueOf(ch)).getValue();
                }
            }
            return flags;
        }
    }

    public static String formatAsBinary(int value) {
        return new StringBuffer(ObjectUtils.formatBinary(value, 5)).reverse().toString();
    }

    public static String formatAsHumanReadable(int value) {
        StringBuilder sb = new StringBuilder();
        for (PermissionFlag f : PermissionFlag.values()) {
            if (f.match(value)) {
                sb.append(f.name());
            } else {
                sb.append('-');
            }
        }
        return sb.toString();
    }
}
