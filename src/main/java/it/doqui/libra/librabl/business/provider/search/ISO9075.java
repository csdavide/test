package it.doqui.libra.librabl.business.provider.search;

import org.apache.commons.lang3.StringUtils;

import javax.xml.namespace.QName;

public class ISO9075 {
    private static final int MASK = 15;
    private static final char[] DIGITS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private ISO9075() {
    }

    public static String encode(String toEncode) {
        if (toEncode != null && toEncode.length() != 0) {
            if (XMLChar.isValidName(toEncode) && toEncode.indexOf("_x") == -1 && toEncode.indexOf(58) == -1) {
                return toEncode;
            } else {
                StringBuilder builder = new StringBuilder(toEncode.length());

                for(int i = 0; i < toEncode.length(); ++i) {
                    char c = toEncode.charAt(i);
                    if (i == 0) {
                        if (XMLChar.isNCNameStart(c)) {
                            if (matchesEncodedPattern(toEncode, i)) {
                                encode('_', builder);
                            } else {
                                builder.append(c);
                            }
                        } else {
                            encode(c, builder);
                        }
                    } else if (!XMLChar.isNCName(c)) {
                        encode(c, builder);
                    } else if (matchesEncodedPattern(toEncode, i)) {
                        encode('_', builder);
                    } else {
                        builder.append(c);
                    }
                }

                return builder.toString();
            }
        } else {
            return toEncode;
        }
    }

    private static boolean matchesEncodedPattern(String string, int position) {
        return string.length() >= position + 6 && string.charAt(position) == '_' && string.charAt(position + 1) == 'x' && isHexChar(string.charAt(position + 2)) && isHexChar(string.charAt(position + 3)) && isHexChar(string.charAt(position + 4)) && isHexChar(string.charAt(position + 5)) && string.charAt(position + 6) == '_';
    }

    private static boolean isHexChar(char c) {
        switch(c) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
                return true;
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case 'G':
            case 'H':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            default:
                return false;
        }
    }

    public static String decode(String toDecode) {
        if (toDecode != null && toDecode.length() >= 7 && toDecode.indexOf("_x") >= 0) {
            StringBuffer decoded = new StringBuffer();
            int i = 0;

            for(int l = toDecode.length(); i < l; ++i) {
                if (matchesEncodedPattern(toDecode, i)) {
                    decoded.append((char)Integer.parseInt(toDecode.substring(i + 2, i + 6), 16));
                    i += 6;
                } else {
                    decoded.append(toDecode.charAt(i));
                }
            }

            return decoded.toString();
        } else {
            return toDecode;
        }
    }

    private static void encode(char c, StringBuilder builder) {
        char[] buf = new char[]{'_', 'x', '0', '0', '0', '0', '_'};
        int charPos = 6;

        do {
            --charPos;
            buf[charPos] = DIGITS[c & 15];
            c = (char)(c >>> 4);
        } while(c != 0);

        builder.append(buf);
    }

    public static String getXPathName(QName qName) {
        String result = encode(qName.getLocalPart());
        if (StringUtils.isNotBlank(qName.getNamespaceURI())) {
            result = "{" + qName.getNamespaceURI() + "}" + result;
        }
        return result;
    }
}
