package it.doqui.libra.librabl.business.provider.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.service.node.PropertyContainer;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.PropertyValueOperation;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.node.*;
import it.doqui.libra.librabl.views.schema.PropertyDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ApplicationScoped
@Slf4j
public class PropertyConverter {

    public static final String TYPE_CONTENT = "d:content";
    public static final String TYPE_ANY = "d:any";
    public static final String TYPE_DATE = "d:date";
    public static final String TYPE_NULL = "d:null";
    public static final String TYPE_DATETIME = "d:datetime";
    public static final String TYPE_MLTEXT = "d:mltext";
    public static final String TYPE_TEXT = "d:text";
    public static final String TYPE_INT = "d:int";
    public static final String TYPE_LONG = "d:long";
    public static final String TYPE_BOOLEAN = "d:boolean";
    public static final String TYPE_FLOAT = "d:float";
    public static final String TYPE_DOUBLE = "d:double";
    public static final String TYPE_QNAME = "d:qname";
    public static final String TYPE_LOCALE = "d:locale";
    public static final String TYPE_CATEGORY = "d:category";
    public static final String TYPE_NODEREF = "d:noderef";

    @Inject
    ObjectMapper objectMapper;

    public PropertyContainer convertProperty(ModelSchema schema, String name, Object value) {
        PropertyDescriptor pd = schema.getProperty(name);
        if (pd == null) {
            if (value == null) {
                return nullProperty(name);
            }

            var err = String.format("Missing property %s in the model", name);
            if (StringUtils.startsWith(name, "ecm-sys:")) {
                log.warn(err);
                return null;
            }

            throw new BadDataException(err);
        }

        return convertProperty(pd, value);
    }

    private PropertyContainer nullProperty(String name) {
        var pd = new PropertyDescriptor();
        pd.setName(name);
        pd.setType(TYPE_NULL);
        var pc = new PropertyContainer();
        pc.setDescriptor(pd);
        return pc;
    }

    public PropertyContainer convertProperty(PropertyDescriptor pd, Object value) {
        PropertyContainer pc = new PropertyContainer();
        pc.setDescriptor(pd);

        if (value != null) {
            try {
                if (pd.isMultiple()) {
                    var foundOp = false;
                    List<Object> list = new ArrayList<>();
                    if (value instanceof Collection) {
                        for (Object item : (Collection<?>) value) {
                            var v = convertPropertyValue(pd, item);
                            if (v instanceof  PropertyValueOperation) {
                                foundOp = true;
                            }
                            list.add(v);
                        }
                    } else {
                        var v = convertPropertyValue(pd, value);
                        if (v instanceof  PropertyValueOperation) {
                            foundOp = true;
                        }
                        list.add(v);
                    }

                    if (foundOp) {
                        var p = new PropertyValueOperation();
                        p.setOp("multi");
                        p.setValue(list);
                        pc.setValue(p);
                    } else {
                        pc.setValue(list);
                    }
                } else if (value instanceof Collection<?> values) {
                    if (!values.isEmpty()) {
                        pc.setValue(convertPropertyValue(pd, values.stream().filter(Objects::nonNull).findFirst()));
                    } else {
                        pc.setValue(Optional.empty());
                    }
                } else {
                    pc.setValue(convertPropertyValue(pd, value));
                }
            } catch (RuntimeException e) {
                log.error("Cannot parse property value {} with descriptor {}", value, pd);
                throw e;
            }
        }

        return pc;
    }

    public Object convertPropertyValue(PropertyDescriptor pd, Object value) {
        if (pd == null) {
            return value;
        }

        if (value instanceof Optional<?> optional && optional.isEmpty()) {
            return Optional.empty();
        }

        if (value instanceof Map<?,?> map && map.isEmpty()) {
            return Optional.empty();
        }

        if (pd.isMultiple() && value instanceof Map<?,?> map && map.containsKey("op")) {
            var p = new PropertyValueOperation();
            p.setOp(ObjectUtils.getAsString(map.get("op")));
            p.setValue(convertPropertyValue(pd, map.get("value")));
            return p;
        }

        switch (pd.getType()) {
            case TYPE_NULL:
            case TYPE_ANY:
                return value;
            case TYPE_TEXT:
                return Optional.ofNullable(value).map(Object::toString).orElse(null);
            case TYPE_INT:
                return Optional.ofNullable(value).map(Object::toString).map(Integer::valueOf).orElse(null);
            case TYPE_LONG:
                return Optional.ofNullable(value).map(Object::toString).map(Long::valueOf).orElse(null);
            case TYPE_BOOLEAN:
                return Optional.ofNullable(value).map(Object::toString).map(Boolean::valueOf).orElse(null);
            case TYPE_FLOAT:
                return Optional.ofNullable(value).map(Object::toString).map(Float::valueOf).orElse(null);
            case TYPE_DOUBLE:
                return Optional.ofNullable(value).map(Object::toString).map(Double::valueOf).orElse(null);
            case TYPE_DATE:
                if (value == null) {
                    return null;
                } else {
                    return DateISO8601Utils.parseAsZonedDateTime(value);
                }

            case TYPE_DATETIME:
                return value == null ? null : DateISO8601Utils.parseAsZonedDateTime(value.toString());
            case TYPE_MLTEXT: {
                if (value instanceof Map<?,?> map) {
                    MLTextProperty ml = new MLTextProperty();
                    map.forEach((k, v) -> ml.put(I18NUtils.parseLocale(k.toString()), v));
                    return ml;
                } else {
                    return value;
                }
            }
            case TYPE_CONTENT: {
                if (value instanceof Collection<?> c) {
                    return c.stream().map(v -> convertContentProperty(pd.getName(), v)).toList();
                } else {
                    return convertContentProperty(pd.getName(), value);
                }
            }
            case TYPE_QNAME:
                return value == null ? null : QName.valueOf(value.toString());
            case TYPE_LOCALE:
                return value == null ? null : I18NUtils.parseLocale(value.toString());
            case TYPE_CATEGORY:
            case TYPE_NODEREF:
                return value == null ? null : URI.create(value.toString());
            default:
                log.warn("Unknown data type {}", pd.getType());
                return value;
        }
    }

    private Object convertContentProperty(String name, Object value) {
        if (value == null) {
            return ContentProperty.of(name);
        } else if (value instanceof ContentBasicDescriptor v) {
            v.setName(name);
            return v;
        } else if (value instanceof Map<?,?> m && m.containsKey("source")) {
            var v = objectMapper.convertValue(value, ExternalContentDescriptor.class);
            v.setName(name);
            return v;
        } else if (value instanceof Map<?,?> m && m.containsKey("contentUrl")) {
            var v = objectMapper.convertValue(value, ContentProperty.class);
            v.setName(name);
            return v;
        } else if (value instanceof Map<?,?> m && m.containsKey("mode")) {
            try {
                var v = new SingleContentOperation();
                v.setMode(ContentOperationMode.valueOf(Optional.ofNullable(m.get("mode")).map(Object::toString).orElse(null)));
                v.setCurrentFileName(Optional.ofNullable(m.get("currentFileName")).map(Object::toString).orElse(null));

                var content = convertContentProperty(name, m.get("value"));
                if (content instanceof ContentBasicDescriptor cd) {
                    v.setValue(cd);
                } else if (v.getMode() != ContentOperationMode.REMOVE) {
                    throw new RuntimeException("missing or invalid value");
                }

                return v;
            } catch (RuntimeException e) {
                throw new BadRequestException("Invalid content operation: " + e.getMessage());
            }
        } else if (value instanceof Map<?,?> m && m.containsKey("data")) {
            var data = m.get("data");
            if (data instanceof String s && Base64.isBase64(s)) {
                try {
                    var cs = objectMapper.convertValue(m, ContentStream.class);
                    cs.setName(name);
                    var buffer = Base64.decodeBase64(s);
                    cs.setSize((long) buffer.length);
                    cs.setInputStream(new ByteArrayInputStream(buffer));
                    return cs;
                } catch (RuntimeException e) {
                    throw new BadRequestException("Invalid content operation: " + e.getMessage());
                }
            }

            return null;
        } else if (value instanceof String s) {
            return Base64.isBase64(s) ? parseAsStream(name, s) : ContentProperty.parse(name, s);
        } else {
            return value;
        }
    }

    private ContentStream parseAsStream(String name, String value) {
        var buffer = Base64.decodeBase64(value);
        var cs = new ContentStream();
        cs.setName(name);
        cs.setSize((long) buffer.length);
        cs.setInputStream(new ByteArrayInputStream(buffer));
        return cs;
    }

    public Object serializePropertyValue(PropertyDescriptor pd, Object value) {
        if (value == null) {
            return null;
        }

        if (pd == null) {
            return value;
        }

        switch (pd.getType()) {
            case TYPE_DATE:
            case TYPE_DATETIME:
                if (pd.isMultiple()) {
                    if (value instanceof Collection<?> collection) {
                        return collection.stream().map(this::convertPropertyAsStorable).toList();
                    } else {
                        return List.of(convertPropertyAsStorable(value));
                    }
                } else {
                    return convertPropertyAsStorable(value);
                }
            default:
                return value;
        }
    }

    private Object convertPropertyAsStorable(Object value) {
        if (value instanceof ZonedDateTime date) {
            return DateISO8601Utils.dateFormat.format(date.truncatedTo(ChronoUnit.MILLIS));
        } else if (value instanceof LocalDate date) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
        }

        return value;
    }
}
