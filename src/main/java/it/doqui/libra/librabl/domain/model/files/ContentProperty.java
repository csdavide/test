package it.doqui.libra.librabl.domain.model.files;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import it.doqui.libra.librabl.foundation.Stringable;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.serialization.UriStringDeserializer;
import it.doqui.libra.librabl.domain.model.document.SignData;
import jakarta.ws.rs.core.MediaType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = ContentDescriptor.class)
public class ContentProperty extends ContentDescriptor implements Stringable, Serializable, FileDescriptor {

    @JsonDeserialize(using = UriStringDeserializer.class)
    private String contentUrl;
    private String hash;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<SignData> signs;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String downloadUrl;

    @JsonIgnore
    private transient UpdateOperation op;

    public ContentProperty() {
        this.signs = new ArrayList<>();
        clear();
    }

    public static ContentProperty of(ContentProperty other) {
        var cp = new ContentProperty();
        cp.copyFrom(other);
        cp.setSize(other.getSize());
        cp.setContentUrl(other.getContentUrl());
        cp.setDownloadUrl(other.getDownloadUrl());
        cp.setOp(other.getOp());
        cp.setHash(other.getHash());
        cp.getSigns().addAll(other.getSigns());
        return cp;
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

    @Override
    public <T extends ContentBasicDescriptor> void mergeWith(T d) {
        super.mergeWith(d);
        if (d instanceof ContentProperty c) {
            if (this.contentUrl == null) {
                this.contentUrl = c.contentUrl;
            }
            if (this.hash == null) {
                this.hash = c.hash;
            }
            if (this.downloadUrl == null) {
                this.downloadUrl = c.downloadUrl;
            }
            if (this.op == null) {
                this.op = c.op;
            }
        }
    }

    public void clear() {
        this.name = null;
        this.contentUrl = null;
        this.mimetype = null;
        this.size = null;
        this.encoding = null;
        this.locale = null;
        this.fileName = null;
        this.downloadUrl = null;
        this.hash = null;
        this.signs.clear();
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
                    String v = b.length > 1 && !Strings.CS.equals(b[1], "null") ? StringUtils.stripToNull(b[1]) : null;
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
            .filter(f -> !Strings.CS.equals(f.getName(), "name"))
            .filter(f -> !Strings.CS.equals(f.getName(), "text"))
            .forEach(f -> fieldMap.put(f.getName(), f));

        Arrays.stream(this.getClass().getSuperclass().getDeclaredFields())
            .filter(f -> !Strings.CS.equals(f.getName(), "name"))
            .filter(f -> !Strings.CS.equals(f.getName(), "text"))
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
        return String.format("%s|fileName=%s|hash=%s",
            toLegacyString(),
            StringUtils.stripToEmpty(fileName),
            StringUtils.stripToEmpty(hash)
        );
    }

    @Getter
    @Setter
    @ToString
    public static class UpdateOperation {
        private ContentOperationMode mode;
        private String currentFileName;
    }

    @Override
    @JsonIgnore
    public URI getFileURI() {
        return Optional.ofNullable(contentUrl).map(URI::create).orElse(null);
    }
}
