package it.doqui.libra.librabl.business.provider.search;

import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.solr.client.solrj.util.ClientUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.temporal.TemporalAdjusters.firstDayOfYear;

@Slf4j
@Setter
public class QueryConverter {
    private static final Pattern fieldPattern = Pattern.compile("@[a-zA-Z][a-zA-Z0-9-_]*\\\\:");
    private static final Pattern starQueryPattern = Pattern.compile("@[a-zA-Z][a-zA-Z0-9-_\\\\:]*:\"\\*");
    private static final Pattern nsPattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9-_]*(\\\\:|:)");
    private static final Pattern alfrescoPattern = Pattern.compile("\\{(http|https)://[a-zA-Z0-9-_/\\\\.]*}");
    private static final Pattern quotedPattern = Pattern.compile("\"[^\"]*\""); // Da verificare se equivalente a Pattern.compile("\"(\\.|[^\"])*\"");
    private static final Pattern qpPattern = Pattern.compile("(QNAME|PATH):\"");
    private static final Pattern uuidPattern = Pattern.compile("(PARENT|@sys\\\\:node-(uu|db)id):");
    private static final Pattern nullPattern = Pattern.compile("(NOT ISNULL|NOT ISNOTNULL|ISNULL|ISNOTNULL):");
    private static final Pattern rangePattern = Pattern.compile("\\[.*(TO|to).*]|\".*\"");

    private static final String QUOTE_DATE_REGEX = "^\\\"*(-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[0-1]|0[1-9]|[1-2][0-9])?T(2[0-3]|[0-1][0-9]):([0-5][0-9]):([0-5][0-9])(\\.[0-9]+)??(Z|[+-](?:2[0-3]|[0-1][0-9]):[0-5][0-9])?\\\"*$";
    private static final String DATE_REGEX = "^(-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[0-1]|0[1-9]|[1-2][0-9])?(T(2[0-3]|[0-1][0-9]):([0-5][0-9]):([0-5][0-9])(\\.[0-9]+)??(Z|[+-](?:2[0-3]|[0-1][0-9]):[0-5][0-9])?)?$";
    private static final Pattern datePattern = Pattern.compile("[1-9]([0-9]){3}-([0-9]){2}-([0-9]){2}T([0-9]){2}:([0-9]){2}:([0-9]){2}(\\.[0-9]+)?(Z|[+-]([0-9]){2}:([0-9]){2})?");

    private static final Set<Character> sepCharSet = Set.of(' ', '[', ']', '(', ')', '{', '}');

    private final TenantRef tenantRef;
    private final ModelSchema schema;
    private final String additionalTokenizedSuffix;
    private boolean retroCompatibilityMode;
    private boolean numericPathEnabled;
    private Function<String,String> pathConvert;
    private Function<String,String> uuidFromPath;

    public QueryConverter(TenantRef tenantRef, ModelSchema schema, String additionalTokenizedSuffix) {
        this.tenantRef = tenantRef;
        this.schema = schema;
        this.pathConvert = null;
        this.uuidFromPath = null;
        this.additionalTokenizedSuffix = additionalTokenizedSuffix;
    }

    public String convertQuery(String q) {
        q = convertDates(q);
        q = convertQueryWithAdditionalTokenizedFields(schema, q);
        q = convertSystemUUIDTerms(q);
        q = convertQuotedFields(schema, q);

        q = convertQueryReplacingAlfrescoNamespaces(schema, q);
        q = convertRanges(schema, q);
        q = convertCheckNullTerms(schema, q);
        q = convertQuotedParentPathWithWildcard(q);
        q = convertQuotedPathWithWildcard(q);
        q = escapeQuotedWildcardTerms(schema, q);

        return q;
    }

    private String convertQuotedParentPathWithWildcard(String q) {
        StringBuilder sb = new StringBuilder(q);
        int i = 0;
        while (i < sb.length()) {
            int start = sb.indexOf("PARENTPATH:\"", i);
            if (start >= 0) {
                int p0 = start + 12;
                int end = sb.indexOf("\"", p0);
                if (end > p0) {
                    String s = sb.substring(p0, end);
                    if (s.startsWith("//")) {
                        if (numericPathEnabled) {
                            throw new BadRequestException("Unsupported query: path cannot start with '//'");
                        }

                        sb.replace(p0, p0 + 2, "*/");
                    }

                    if (s.endsWith("/")) {
                        if (numericPathEnabled) {
                            var target = uuidFromPath.apply(s);
                            sb.replace(p0, end, target);
                            end += target.length() - end + p0;

                            sb.replace(start, start + 10, "PARENT");
                            end -= 4;
                        } else {
                            sb.replace(end - 1, end, "");
                            end--;
                        }
                    } else if (s.endsWith("/*")) {
                        if (numericPathEnabled) {
                            var target = pathConvert.apply(s.substring(0, s.length() - (s.endsWith("//*") ? 2 : 1)));
                            var pathArray = target.split(":");
                            log.debug("Got path array: '{}'", Arrays.toString(pathArray));
                            pathArray[pathArray.length - 1] = "*";
                            target = String.join(":", pathArray);

                            sb.replace(p0, end - 1, target);
                            end += target.length() - end + 1 + p0;

                            sb.replace(start, start + 10, "NODEPATH");
                            end -= 2;
                        } else if (s.endsWith("//*")) {
                            sb.replace(end - 2, end - 1, "");
                            end--;
                        }
                    } else if (numericPathEnabled) {
                        var target = uuidFromPath.apply(s + "/");
                        sb.replace(p0, end, target);
                        end += target.length() - end + p0;

                        sb.replace(start, start + 10, "PARENT");
                        end -= 4;
                    }

                    i = end + 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return sb.toString();
    }

    private String convertQuotedPathWithWildcard(String q) {
        log.trace("convertQuotedPathWithWildcard Q = {}", q);
        StringBuilder sb = new StringBuilder(q);
        int i = 0;
        while (i < sb.length()) {
            int start = sb.indexOf("PATH:\"", i);
            if (start == 0 || (start > 0 && !Character.isAlphabetic(sb.charAt(start - 1)))) {
                int p0 = start + 6;
                int end = sb.indexOf("\"", p0);
                if (end > p0) {
                    String s = sb.substring(p0, end);
                    if (s.startsWith("//")) {
                        if (numericPathEnabled) {
                            throw new BadRequestException("Unsupported query: path cannot start with '//'");
                        }

                        sb.replace(p0, p0 + 2, "*/");
                    }

                    if (s.endsWith("/")) {
                        if (numericPathEnabled) {
                            var target = pathConvert.apply(s);
                            sb.replace(p0, end, target);
                            end += target.length() - end + p0;

                            sb.replace(start, start + 4, "NODEPATH");
                            end += 4;
                        } else {
                            sb.replace(end - 1, end, "");
                            end--;
                        }
                    } else if (s.endsWith("//*")) {
                        if (numericPathEnabled) {
                            var target = pathConvert.apply(s.substring(0, s.length() - 2));
                            sb.replace(p0, end - 1, target);
                            end += target.length() - end + 1 + p0;

                            sb.replace(start, start + 4, "NODEPATH");
                            end += 4;
                        } else {
                            sb.replace(end - 2, end - 1, "");
                            end--;
                        }
                    } else if (s.endsWith("/*")) {
                        if (numericPathEnabled) {
                            var target = uuidFromPath.apply(s.substring(0, s.length() - 1));
                            sb.replace(p0, end, target);
                            end += target.length() - end + p0;

                            sb.replace(start, start + 4, "PARENT");
                            end += 2;
                        } else {
                            sb.replace(end - 2, end, "");
                            end -= 2;

                            sb.replace(start, start + 4, "PARENTPATH");
                            end += 6;
                        }
                    } else if (numericPathEnabled) {
                        var target = pathConvert.apply(s + '/');
                        sb.replace(p0, end, target);
                        end += target.length() - end + p0;

                        sb.replace(start, start + 4, "NODEPATH");
                        end += 4;
                    }

                    i = end + 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return sb.toString();
    }

    private String escapeQuotedWildcardTerms(ModelSchema schema, final String q) {
        log.trace("escapeQuotedWildcardTerms Q = {}", q);
        StringBuilder sb = new StringBuilder(q);
        int i = 0;
        while (i < sb.length()) {
            int start = sb.indexOf("\"", i);
            if (start >= 0) {
                int end = sb.indexOf("\"", start + 1);
                if (end > start + 1) {
                    // found quoted string
                    var et = isEscapeRequired(schema, sb, start, end);
                    if (et.getLeft()) {
                        String s = sb.substring(start + 1, end);
                        int len = s.length();
                        log.trace("Escaping string {} (replace jolly {})", s, et.getRight());
                        s = ClientUtils.escapeQueryChars(s);
                        s = s.replace("\\*", et.getRight() ? "" : "*");
                        s = s.replace("\\?", et.getRight() ? "" : "?");
                        log.trace("Escaped string {}", s);
                        sb.replace(start, end + 1, s);
                        end += s.length() - len - 2;
                    }

                    i = end + 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return sb.toString();
    }

    private Pair<Boolean,Boolean> isEscapeRequired(final ModelSchema schema, final StringBuilder sb, final int start, final int end) {
        int star = sb.indexOf("*", start + 1);
        int quest = sb.indexOf("?", start + 1);
        log.trace("Analyzing fragment '{}' star = {}, quest = {}, start = {}, end = {}", sb.substring(start, end), star, quest, start, end);
        if ((star > start && star < end) || (quest > start && quest < end)) {
            if (start == end - 2) {
                return new ImmutablePair<>(true,false);
            }

            // check tokenized term
            if (start > 0 && sb.charAt(start - 1) == ':') {
                if (start > 4 && "TEXT".equals(sb.substring(start - 5, start - 1))) {
                    return new ImmutablePair<>(true,true);
                } else {
                    int at = sb.lastIndexOf("@", start - 1);
                    if (at >= 0) {
                        var pname = sb.substring(at + 1, start - 1).replace("\\:", ":");
                        if (pname.codePoints().mapToObj(c -> (char) c).anyMatch(sepCharSet::contains)) {
                            return new ImmutablePair<>(true,false);
                        }

                        var pd = Optional.ofNullable(schema.getNamespaceSchema(pname)).map(cs -> cs.getProperty(pname)).orElse(null);
                        log.trace("Checking property '{}' for escape: {}", pname, pd);
                        return new ImmutablePair<>(true,pd == null || pd.isTokenized());
                    }
                }
            }

            return new ImmutablePair<>(true,false);
        } else {
            return new ImmutablePair<>(false,false);
        }
    }

//    private String convertQueryWithAlfrescoFields(ModelSchema schema, final String q) {
//        Matcher matcher = fieldPattern.matcher(q);
//        String r = q;
//        while (matcher.find()) {
//            String g = matcher.group();
//            String prefix = g.substring(1, g.length() - 2);
//
//            CustomModelSchema customModelSchema = schema.getNamespaceSchema(prefix);
//            String target = customModelSchema.getNamespace(prefix)
//                .map(URI::toString)
//                .map(s -> s.replace("http:", "http\\:").replace("https:", "https\\:"))
//                .map(s -> String.format("@\\{%s\\}\\:", s))
//                .orElse(null);
//
//            log.trace("MATCHER FOUND: {} -> {}", prefix, target);
//            if (target != null) {
//                r = r.replace(g, target);
//            }
//        }
//
//        return r;
//    }

    private String convertQueryWithAdditionalTokenizedFields(ModelSchema schema, final String q) {
        Matcher matcher = starQueryPattern.matcher(q);
        String r = q;
        while (matcher.find()) {
            String g = matcher.group();
            String s = g.substring(1, g.length() - 3);
            var pd = schema.getProperty(s.replace("\\:", ":"));
            if (pd != null && pd.isAdditionalTokenizedFieldRequired()) {
                var target = "@" + s + additionalTokenizedSuffix + ":\"*";
                log.trace("Custom field converted: {} -> {}", g, target);
                r = r.replace(g, target);
            }
        }

        return r;
    }

    private String convertQuotedFields(ModelSchema schema, final String q) {
        Matcher matcher = quotedPattern.matcher(q);
        var r = q;
        while (matcher.find()) {
            var g = matcher.group();
            var s = g.substring(1, g.length() - 1);
            if (StringUtils.startsWith(s, "workspace:")) {
                var p = s.lastIndexOf('/');
                if (p > 0) {
                    r = r.replace(s, s.substring(p + 1));
                }
            }
        }

        return r;
    }

    private String convertDates(final String q) {
        Matcher matcher = datePattern.matcher(q);
        var r = q;
        while (matcher.find()) {
            var g = matcher.group();
            log.debug("Found date: {}", g);
            String s = DateTimeFormatter.ISO_INSTANT.format(DateISO8601Utils.parseAsZonedDateTime(g));
            r = r.replace(g, s);
        }

        return r;
    }

//    private String convertQueryWithAlfrescoNamespaces(ModelSchema schema, final String q) {
//        Matcher matcher = nsPattern.matcher(q);
//        String r = q;
//        while (matcher.find()) {
//            String g = matcher.group();
//            boolean escaped = g.endsWith("\\:");
//            String prefix = g.substring(0, g.length() - (escaped ? 2 : 1));
//            CustomModelSchema customModelSchema = schema.getNamespaceSchema(prefix);
//            if (customModelSchema != null) {
//                String target = customModelSchema.getNamespace(prefix)
//                    .map(URI::toString)
//                    .map(s -> String.format("{%s}", s))
//                    .orElse(null);
//
//                log.trace("MATCHER FOUND: {} escaped: {} prefix: {} -> {}", g, escaped, prefix, target);
//                if (target != null) {
//                    if (escaped) {
//                        target = target
//                            .replace("{", "\\{")
//                            .replace("}", "\\}")
//                            .replace("http:", "http\\:")
//                            .replace("https:", "https\\:");
//                    }
//
//                    r = r.replace(g, target);
//                }
//            }
//        } // end while
//
//        return r;
//    }

    private String convertQueryReplacingAlfrescoNamespaces(ModelSchema schema, final String q) {
        Matcher matcher = alfrescoPattern.matcher(q);
        String r = q;
        while (matcher.find()) {
            try {
                var g = matcher.group();
                var uri = new URI(g.substring(1, g.length() - 1));
                var prefix = schema.getNamespace(uri);
                if (prefix != null) {
                    r = r.replace(g, prefix + "\\:");
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        return r;
    }

//    private String encodeISO9075Terms(String q) {
//        Matcher matcher = qpPattern.matcher(q);
//        StringBuilder sb = new StringBuilder();
//        int p = 0;
//        while (matcher.find()) {
//            String g = matcher.group();
//            log.trace("encodeISO9075Terms FOUND: {} START: {} END: {}", g, matcher.start(), matcher.end());
//            if (matcher.start() >= p) {
//                int quote = q.indexOf("\"", matcher.end());
//                if (quote >= matcher.end()) {
//                    sb.append(q, p, matcher.end());
//
//                    String s = q.substring(matcher.end(), quote);
//                    String r = Arrays.stream(s.split("/"))
//                        .map(PrefixedQName::valueOf)
//                        .map(qname -> new PrefixedQName(qname.getNamespaceURI(), ISO9075.encode(qname.getLocalPart())))
//                        .map(PrefixedQName::toString)
//                        .collect(Collectors.joining("/"));
//
//                    sb.append(r);
//                    sb.append("\"");
//                    p = quote + 1;
//                }
//            }
//        }
//
//        if (p < q.length() - 1) {
//            sb.append(q.substring(p));
//        }
//
//        return sb.toString();
//    }

//    private String convertAlfrescoUUIDTerms(String q) {
//        Matcher matcher = uuidPattern.matcher(q);
//        StringBuilder sb = new StringBuilder();
//        int p = 0;
//        while (matcher.find()) {
//            String g = matcher.group();
//            log.trace("FOUND NODE-ID: {} START: {} END: {}", g, matcher.start(), matcher.end());
//            if (matcher.start() >= p) {
//                int quote = q.indexOf("\"", matcher.end());
//                if (quote >= matcher.end()) {
//                    sb.append(q, p, matcher.start());
//                    String s = q.substring(matcher.end(), quote);
//
//                    if (StringUtils.startsWith(g, "@sys\\:node-dbid")) {
//                        sb.append("DBID:");
//                        sb.append(s);
//                    } else {
//                        if (StringUtils.startsWith(g, "@sys\\:node-uuid")) {
//                            sb.append("ID:");
//                        } else {
//                            sb.append(q, matcher.start(), matcher.end());
//                        }
//
//                        if (StringUtils.startsWith(s, "workspace:")) {
//                            sb.append(s);
//                        } else {
//                            String tenant = tenantRef.getName();
//                            String t = StringUtils.equals(tenant, DEFAULT_TENANT) ? "" : String.format("@%s@", tenant);
//                            sb.append(String.format("workspace://%sSpacesStore/%s", t, s));
//                        }
//                    }
//
//                    sb.append("\"");
//                    p = quote + 1;
//                }
//            }
//        }
//
//        if (p < q.length() - 1) {
//            sb.append(q.substring(p));
//        }
//
//        return sb.toString();
//    }

    private String convertSystemUUIDTerms(String q) {
        return q.replace("@sys\\:node-dbid:", "DBID:").replace("@sys\\:node-uuid:", "ID:");

//        Matcher matcher = uuidPattern.matcher(q);
//        StringBuilder sb = new StringBuilder();
//        int p = 0;
//        while (matcher.find()) {
//            String g = matcher.group();
//            log.trace("FOUND NODE-ID: {} START: {} END: {}", g, matcher.start(), matcher.end());
//            if (matcher.start() >= p) {
//                int quote = q.indexOf("\"", matcher.end());
//                if (quote >= matcher.end()) {
//                    sb.append(q, p, matcher.start());
//                    String s = q.substring(matcher.end(), quote);
//
//                    if (StringUtils.startsWith(g, "@sys\\:node-dbid")) {
//                        sb.append("DBID:");
//                        sb.append(s);
//                    } else {
//                        if (StringUtils.startsWith(g, "@sys\\:node-uuid")) {
//                            sb.append("ID:");
//                        } else {
//                            sb.append(q, matcher.start(), matcher.end());
//                        }
//                    }
//
//                    sb.append(s);
//                    sb.append("\"");
//                    p = quote + 1;
//                }
//            }
//        }
//
//        if (p < q.length() - 1) {
//            sb.append(q.substring(p));
//        }
//
//        return sb.toString();
    }

    private String convertCheckNullTerms(ModelSchema schema, String q) {
        Matcher matcher = nullPattern.matcher(q);
        StringBuilder sb = new StringBuilder();
        int p = 0;
        while (matcher.find()) {
            String g = matcher.group();
            if (matcher.start() >= p && matcher.end() < q.length()) {
                sb.append(q, p, matcher.start());
                p = matcher.start();
                boolean quoted = q.charAt(matcher.end()) == '\"';
                final String s;
                if (quoted) {
                    int quote = q.indexOf('\"', matcher.end() + 1);
                    if (quote > matcher.end()) {
                        var quotedString = StringUtils.stripToEmpty(q.substring(matcher.end() + 1, quote));
                        if (!StringUtils.startsWith(quotedString, "@") && schema.getNamespaceSchema(quotedString) != null) {
                            quotedString = "@" + quotedString;
                        }

                        s = ClientUtils.escapeQueryChars(quotedString);
                        p = quote + 1;
                    } else {
                        break;
                    }
                } else {
                    int end = q.length();
                    for (int i=matcher.end(); i<q.length(); i++) {
                        if (q.charAt(i) == ' ' && q.charAt(i - 1) != '\\') {
                            end = i;
                            break;
                        }
                    }

                    s = q.substring(matcher.end(), end);
                    p = end;
                }

                if (g.startsWith("ISNULL:") || g.startsWith("NOT ISNOTNULL:")) {
                    sb.append("!");
                }

                sb.append(s);
                sb.append(":*");
            }
        }

        if (p < q.length() - 1) {
            sb.append(q.substring(p));
        }

        return sb.toString();
    }

    private String convertRanges(ModelSchema schema, String q) {
        Matcher matcher = rangePattern.matcher(q);
        StringBuilder sb = new StringBuilder();
        int p = 0;
        while (matcher.find()) {
            sb.append(q, p, matcher.start());
            p = matcher.start();
            String g = matcher.group();
            if (g.startsWith("[")) {
                String noSquare = g.substring(1, g.length() - 1).replace(" to ", " TO ");
                String[] elems = noSquare.split(" TO ");
                if (elems.length == 2) {
                    String id = null;
                    var p0 = sb.lastIndexOf("@", matcher.start());
                    if (p0 >= 0) {
                        var p1 = sb.lastIndexOf(":", matcher.start());
                        if (p1 > p0) {
                            id = sb.substring(p0 + 1, p1).replace("\\:", ":");
                        }
                    }

                    elems[0] = convertRangeElement(schema, id, elems[0], true);
                    elems[1] = convertRangeElement(schema, id, elems[1], false);

                    String s = "[" + elems[0] + " TO " + elems[1] + "]";
                    sb.append(s);
                    p = matcher.end();
                }
            } else if (g.startsWith("\"") && g.matches(QUOTE_DATE_REGEX)) {
                // quoted date
                String noQuote = g.substring(1, g.length() - 1);
                String s = String.format("\"%s\"", DateTimeFormatter.ISO_INSTANT.format(DateISO8601Utils.parseAsZonedDateTime(noQuote)));
                sb.append(s);
                p = matcher.end();
            }
        }

        if (p < q.length() - 1) {
            sb.append(q.substring(p));
        }

        return sb.toString();
    }

    private String convertRangeElement(ModelSchema schema, String id, String elem, boolean beginning) {
        if (elem.matches(DATE_REGEX)) {
            return DateTimeFormatter.ISO_INSTANT.format(DateISO8601Utils.parseAsZonedDateTime(elem));
        } else if (id != null) {
            var pd = schema.getProperty(id);
            if (pd != null) {
                switch (elem.toUpperCase()) {
                    case "MAX", "MIN":
                        elem = "*";
                        break;

                    default:
                        switch (pd.getType()) {
                            case "d:date", "d.datetime":
                                if (StringUtils.isNumeric(elem)) {
                                    try {
                                        var value = Integer.parseInt(elem);
                                        var date = LocalDate.now().withYear(beginning ? value : value + 1);
                                        var d = date.with(firstDayOfYear()).atStartOfDay(ZoneId.systemDefault());
                                        elem = DateTimeFormatter.ISO_INSTANT.format(d);
                                    } catch (Exception e) {
                                        log.error(String.format("Unable to parse number '%s': %s", elem, e.getMessage()));
                                    }
                                }
                                break;
                        }
                        break;
                }
            }
        }

        return elem;
    }

}
