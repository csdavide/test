package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.foundation.Stringable;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.ws.rs.core.MediaType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = ContentDescriptor.class)
public class ContentProperty extends ContentDescriptor implements Stringable, Serializable {

    private String contentUrl;

    @JsonIgnore
    private transient String text;

    @JsonIgnore
    private transient UpdateOperation op;

    public ContentProperty() {
        clear();
    }

    public static ContentProperty of(String name) {
        var cp = new ContentProperty();
        cp.setName(name);
        return cp;
    }

    public static ContentProperty of(String name, Map<?, ?> attributes) {
        var cp = new ContentProperty();
        cp.setName(name);
        cp.setAttributes(attributes);
        return cp;
    }

    public static ContentProperty parse(String name, String value) {
        var cp = new ContentProperty();
        cp.setName(name);
        cp.parseAttributes(value);
        return cp;
    }

    public void clear() {
        this.name = null;
        this.contentUrl = null;
        this.mimetype = null;
        this.size = null;
        this.encoding = null;
        this.locale = null;
        this.fileName = null;
    }

    private void parseAttributes(String value) {
        this.clear();
        Map<String,Field> fieldMap = new HashMap<>();
        Class<?> clazz = this.getClass();
        while (clazz != null) {
            Arrays.stream(clazz.getDeclaredFields()).forEach(f -> fieldMap.put(f.getName(), f));
            clazz = clazz.getSuperclass();
        }

        String[] a = value.split("\\|");
        for (String s : a) {
            String[] b = s.split("=");
            if (b.length > 0) {
                String k = b[0];
                if (!"name".equals(k) && !"text".equals(k)) {
                    String v = b.length > 1 && !StringUtils.equals(b[1], "null") ? StringUtils.stripToNull(b[1]) : null;
                    Field f = fieldMap.get(k);
                    if (f != null && v != null) {
                        try {
                            if (Long.class.equals(f.getType())) {
                                f.set(this, Long.parseLong(v));
                            } else {
                                f.set(this, v);
                            }
                        } catch (IllegalAccessException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private void setAttributes(Map<?, ?> map) {
        Map<String,Field> fieldMap = new HashMap<>();
        Arrays.stream(this.getClass().getDeclaredFields())
            .filter(f -> !StringUtils.equals(f.getName(), "name"))
            .filter(f -> !StringUtils.equals(f.getName(), "text"))
            .forEach(f -> fieldMap.put(f.getName(), f));

        Arrays.stream(this.getClass().getSuperclass().getDeclaredFields())
            .filter(f -> !StringUtils.equals(f.getName(), "name"))
            .filter(f -> !StringUtils.equals(f.getName(), "text"))
            .forEach(f -> fieldMap.put(f.getName(), f));

        map.forEach((key, value) -> {
            Field f = fieldMap.get(key.toString());
            if (f != null) {
                try {
                    if (value == null) {
                        f.set(this, null);
                    } else if (Long.class.equals(f.getType())) {
                        f.set(this, Long.parseLong(value.toString()));
                    } else {
                        f.set(this, value.toString());
                    }
                } catch (IllegalAccessException e) {
                    throw new SystemException(e);
                }
            }
        });
    }

    public String toLegacyString() {
        return String.format("contentUrl=%s|mimetype=%s|size=%d|encoding=%s|locale=%s",
            StringUtils.stripToEmpty(contentUrl),
            StringUtils.isBlank(mimetype) ? MediaType.APPLICATION_OCTET_STREAM : mimetype,
            Optional.ofNullable(size).orElse(0L),
            StringUtils.isBlank(encoding) ? StandardCharsets.UTF_8.toString() : encoding,
            StringUtils.isBlank(locale) ? Locale.getDefault().toString() : locale
        );
    }

    @Override
    public String toString() {
        return String.format("%s|fileName=%s",
            toLegacyString(),
            StringUtils.stripToEmpty(fileName)
        );
    }

    @Getter
    @Setter
    @ToString
    public static class UpdateOperation {
        private ContentOperationMode mode;
        private String currentFileName;
    }
}
