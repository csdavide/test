package it.doqui.libra.librabl.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectUtils {

    private ObjectUtils(ObjectMapper objectMapper) {
        throw new IllegalStateException("Utility class");
    }

    public static void requireNull(Object obj, String message) {
        if (obj != null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void requireNotBlank(Object obj, String message) {
        if (obj == null || StringUtils.isBlank(obj.toString())) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <X extends Throwable> void requireTrue(boolean condition, Supplier<? extends X> exceptionSupplier) throws X {
        if (!condition) {
            throw exceptionSupplier.get();
        }
    }

    public static String getAsString(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Collection<?> c) {
            return c.stream().map(Object::toString).collect(Collectors.joining(","));
        }

        return obj.toString();
    }

    public static Collection<String> getAsStrings(Object obj) {
        if (obj == null) {
            return List.of();
        }

        if (obj instanceof Collection<?> c) {
            return c.stream().map(Object::toString).collect(Collectors.toList());
        }

        return Arrays.stream(obj.toString().split(","))
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    public static boolean getAsBoolean(Object obj, boolean defaultValue) {
        if (obj == null) {
            return defaultValue;
        }

        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof Collection<?> c) {
            return BooleanUtils.toBoolean(
                c.stream().findFirst()
                    .map(Object::toString)
                    .map(BooleanUtils::toBoolean)
                    .orElse(defaultValue));
        } else {
            return BooleanUtils.toBoolean(obj.toString());
        }
    }

    public static long getAsLong(Object obj, long defaultValue) {
        if (obj == null) {
            return defaultValue;
        }

        if (obj instanceof Number n) {
            return n.longValue();
        } else if (obj instanceof Collection<?>) {
            var object = get(((Collection<?>) obj).stream().findFirst());
            return object != null ? Long.parseLong(object.toString()) : defaultValue;
        } else {
            return Long.parseLong(obj.toString());
        }
    }

    public static Integer getAsInteger(Object obj, Integer defaultValue) {
        if (obj == null) {
            return defaultValue;
        }

        if (obj instanceof Number n) {
            return n.intValue();
        } else if (obj instanceof Collection<?>) {
            var object = get(((Collection<?>) obj).stream().findFirst());
            return object != null ? Integer.parseInt(object.toString()) : defaultValue;
        } else {
            return Integer.parseInt(obj.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAsGeneric(Object obj) throws Exception {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Collection<?>) {
            throw new Exception("Collections not supported");
        }
        return (T) obj;
    }

    @SuppressWarnings("unchecked")
    public static <T> Collection<T> getAsCollectionOfGeneric(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Collection<?> c) {
            return (Collection<T>) c;
        } else {
            return List.of((T) obj);
        }
    }

    @SafeVarargs
    public static <T> List<T> asList(T... objects) {
        return objects == null ? List.of() : Arrays.asList(objects);
    }

    public static <T> Set<T> asSet(Collection<T> collection) {
        return collection == null ? Set.of() : new HashSet<>(collection);
    }

    public static <T> Set<T> asNullableSet(Collection<T> collection) {
        return collection == null ? null : asSet(collection);
    }

    public static String[] strings(Object... values) {
        if (values == null || values.length == 0) {
            return null;
        }

        return Arrays.stream(values)
            .map(v -> Optional.ofNullable(v).map(Object::toString).orElse(null))
            .toList()
            .toArray(new String[0]);
    }

    @SafeVarargs
    public static <T> T coalesce(T... objects) {
        for (T obj : objects) {
            if (obj != null) {
                return obj;
            }
        }

        return null;
    }

    public static String formatBinary(int i, int digits) {
        return String.format(String.format("%%%ds", digits), Integer.toBinaryString(i))
            .replace(' ', '0');
    }

    @SuppressWarnings("ALL")
    public static <T> T get(Optional<T> opt) {
        return Optional.ofNullable(opt)
            .map(x -> x.orElse(null))
            .orElse(null);
    }

    @SuppressWarnings("ALL")
    public static <T> T getIfDefined(Optional<T> newValue, T currentValue, boolean replace) {
        return Optional.ofNullable(newValue)
            .map(x -> x.orElse(null))
            .orElse(replace ? null : currentValue);
    }

    public static String hash(String s, String usingAlg) throws NoSuchAlgorithmException {
        final byte[] buffer;
        switch (usingAlg) {
            case "CLEAR":
                return s;

            case "MD4":
                buffer = StringUtils.stripToEmpty(s).getBytes(StandardCharsets.UTF_16LE);
                break;

            default:
                buffer = s.getBytes();
                break;
        }

        return hash(buffer, usingAlg);
    }

    public static String hash(char[] chars, String usingAlg) throws NoSuchAlgorithmException {
        final String charsetName;
        if (usingAlg.equals("MD4")) {
            charsetName = StandardCharsets.UTF_16LE.name();
        } else {
            charsetName = StandardCharsets.UTF_8.name();
        }

        ByteBuffer bb = Charset.forName(charsetName).encode(CharBuffer.wrap(chars));
        byte[] buffer = new byte[bb.remaining()];
        bb.get(buffer);
        return hash(buffer, usingAlg);
    }

    public static String hash(byte[] buffer, String usingAlg) throws NoSuchAlgorithmException {
        switch (usingAlg) {
            case "CLEAR":
                return new String(buffer);

            case "MD4":
                byte[] encPwd = new MD4().digest(buffer);
                return encodeHexString(encPwd);

            default:
                MessageDigest md = MessageDigest.getInstance(usingAlg);
                md.update(buffer);
                byte[] digest = md.digest();
                return DatatypeConverter.printHexBinary(digest).toLowerCase();
        }
    }

    public static String encodeHexString(byte[] byteArray) {
        StringBuilder hexStringBuffer = new StringBuilder();
        for (byte b : byteArray) {
            hexStringBuffer.append(byteToHex(b));
        }
        return hexStringBuffer.toString();
    }

    public static String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits).toLowerCase();
    }

    public static void add(Map<String,Object> map, String key, Object value) {
        final var values = new ArrayList<>();
        var v = map.get(key);
        if (v instanceof Collection<?> prev) {
            values.addAll(prev);
        } else if (v != null) {
            values.add(v);
        }

        if (value instanceof Collection<?> c) {
            values.addAll(c);
        } else {
            values.add(value);
        }
        map.put(key, values);
    }

    public static <T> void add(List<T> list, T value) {
        if (list != null && value != null) {
            list.add(value);
        }
    }

    public static <K, V> void addValueInSet(Map<K, Set<V>> map, K key, V value) {
        var v = map.get(key);
        if (v == null) {
            map.put(key, new HashSet<>(Collections.singleton(value)));
        } else {
            v.add(value);
            map.put(key, v);
        }
    }

    public static <K, V> void addValueInCollection(Map<K, Collection<V>> map, K key, V value) {
        var v = map.get(key);
        if (v == null) {
            map.put(key, new ArrayList<>(Collections.singleton(value)));
        } else {
            v.add(value);
            map.put(key, v);
        }
    }

    public static <K, V> void removeValueFromCollection(Map<K, Collection<V>> map, K key, V value) {
        if (value == null) {
            return;
        }
        var v = map.get(key);
        if (v == null || v.isEmpty()) {
            return;
        }
        v.remove(value);
        if (v.isEmpty()) {
            map.remove(key);
        } else {
            map.put(key, v);
        }
    }

    public static <T,R> List<R> map(Collection<T> items, Function<T, R> mapper) {
        return items.stream().map(mapper).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static <T extends Enum<T>> T valueOf(Class<T> enumClass, Object value) {
        if (value == null) {
            return null;
        }

        try {
            return T.valueOf(enumClass, StringUtils.stripToEmpty(value.toString()).toUpperCase());
        } catch (Exception e) {
            throw new BadDataException("Invalid option " + value);
        }
    }

    public static <T extends Enum<T>> Set<T> valueOf(Class<T> enumClass, List<String> options) {
        final Set<T> optionSet = new HashSet<>();
        if (options != null) {
            for (String s : options) {
                try {
                    Arrays.stream(s.split(",")).forEach(x -> optionSet.add(T.valueOf(enumClass, StringUtils.stripToEmpty(x).toUpperCase())));
                } catch (Exception e) {
                    throw new BadDataException("Invalid options");
                }
            }
        }
        return optionSet;
    }

    public static <T> List<T> append(List<T> list, T element) {
        return Stream.concat(list.stream(), Stream.of(element)).toList();
    }

    public static <T> List<T> append(T element, List<T> list) {
        return Stream.concat(Stream.of(element), list.stream()).toList();
    }

    public static <T> List<T> append(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    public static String takeRegexPart(String s, String regex, int regexGroup, boolean caseSensitive) {
        var matcher = Pattern.compile(regex).matcher(s);
        if (matcher.find()) {
            var result = matcher.group(regexGroup);
            return caseSensitive ? result : result.toLowerCase();
        }
        return null;
    }

    public static String takeElem(List<String> list, String key) {
        return list.stream()
            .filter(s -> s.startsWith(key + "="))
            .findFirst()
            .map(x -> Optional.ofNullable(x.split("=")[1]).orElse(""))
            .orElse(null);
    }

    public static boolean isCauseBy(Throwable e, Class<? extends Throwable> clazz) {
        Throwable cause = e;
        while (cause != null && !clazz.isInstance(cause)) {
            cause = cause.getCause();
        }

        return cause != null;
    }

    public static String encodeUri(String s) {
        if (s == null) {
            return null;
        }

        var p = s.indexOf("://");
        if (p > 0) {
            var prefix = s.substring(0, p);
            var suffix = s.substring(p + 3);

            suffix = suffix
                    .replace("%", "%25")
                    .replace(" ", "%20")
                    .replace("?", "%3F")
                    .replace("#", "%23")
                    .replace("&", "%26")
                    .replace("=", "%3D")
                    .replace("+", "%2B")
                    .replace(":", "%3A")
                    .replace("@", "%40")
                    .replace("$", "%24")
                    .replace(",", "%2C")
                    .replace(";", "%3B")
                    .replace("!", "%21")
                    .replace("*", "%2A")
                    .replace("'", "%27")
                    .replace("^", "%5E")
                    .replace("(", "%28")
                    .replace(")", "%29")
                    .replace("[", "%5B")
                    .replace("]", "%5D");

            return prefix + "://" + suffix;
        }

        return s;
    }
}
