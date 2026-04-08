package it.doqui.libra.librabl.application.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.application.model.properties.MLTextProperty;
import it.doqui.libra.librabl.application.model.properties.PropertyObject;
import it.doqui.libra.librabl.application.model.properties.PropertyValueOperation;
import it.doqui.libra.librabl.domain.model.files.ContentBasicDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentProperty;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.files.Streamable;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.model.schema.PropertyContainer;
import it.doqui.libra.librabl.domain.model.schema.PropertyDescriptor;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.I18NUtils;
import it.doqui.libra.librabl.utils.IOUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.application.model.properties.PropertyValueOperation.PropertyValueOperationType.MULTI;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.*;

@ApplicationScoped
@Slf4j
public class PropertyConverter {

    @Inject
    ObjectMapper objectMapper;

    public PropertyContainer convertProperty(ModelSchema schema, String name, Object value) {
        PropertyDescriptor pd = schema.getProperty(name);
        if (pd == null) {
            if (value == null) {
                return nullProperty(name);
            }

            var err = String.format("Missing property %s in the model", name);
            if (Strings.CS.startsWith(name, "ecm-sys:")) {
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
                            if (v instanceof PropertyValueOperation) {
                                foundOp = true;
                            }
                            list.add(v);
                        }
                    } else {
                        var v = convertPropertyValue(pd, value);
                        if (v instanceof PropertyValueOperation) {
                            foundOp = true;
                        }
                        list.add(v);
                    }

                    if (foundOp) {
                        var p = new PropertyValueOperation();
                        p.setOp(MULTI);
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

    public Object convertValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map<?,?> map) {
            if (map.isEmpty()) {
                return Optional.empty();
            }

            if (!map.containsKey("kind") && map.containsKey("op")) {
                // atlas retro-compatibility
                var m = new HashMap<String,Object>(map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)));
                m.put("kind", "valueOperation");
                map = m;
            }

            value = objectMapper.convertValue(map, PropertyObject.class);
            if (value instanceof PropertyValueOperation pvo) {
                var v = convertValue(pvo.getValue());
                if (v instanceof PropertyValueOperation) {
                    throw new IllegalArgumentException("Cannot nest property value operations");
                }

                pvo.setValue(v);
            }
        }

        return value;
    }

    public Object convertPropertyValue(PropertyDescriptor pd, Object value) {
        if (pd == null) {
            return value;
        }

        if (value instanceof Optional<?> optional && optional.isEmpty()) {
            return Optional.empty();
        }

        if (value instanceof Map<?,?> map) {
            value = convertValue(map);
        }

        if (value instanceof PropertyValueOperation) {
            return value;
        }

        switch (pd.getType()) {
            case TYPE_NULL:
            case TYPE_ANY:
                return value;
            case TYPE_STRING:
            case TYPE_TEXT:
                if (value instanceof Streamable s) {
                    try {
                        var cs = s.asStream();
                        value = new String(IOUtils.readFully(cs.getInputStream()), Optional.ofNullable(cs.getEncoding()).orElse(StandardCharsets.UTF_8.name()));
                    } catch (IOException e) {
                        throw new SystemException(e);
                    }
                }
                return Optional.ofNullable(value).map(Object::toString).orElse(null);
            case TYPE_TEXT_CI:
                return Optional.ofNullable(value).map(Object::toString).orElse(null);
            case TYPE_INT:
                return Optional.ofNullable(value).map(Object::toString).map(StringUtils::stripToNull).map(Integer::valueOf).orElse(null);
            case TYPE_LONG:
                return Optional.ofNullable(value).map(Object::toString).map(StringUtils::stripToNull).map(Long::valueOf).orElse(null);
            case TYPE_BOOLEAN:
                return Optional.ofNullable(value).map(Object::toString).map(StringUtils::stripToNull).map(Boolean::valueOf).orElse(null);
            case TYPE_FLOAT:
                return Optional.ofNullable(value).map(Object::toString).map(StringUtils::stripToNull).map(Float::valueOf).orElse(null);
            case TYPE_DOUBLE:
                return Optional.ofNullable(value).map(Object::toString).map(StringUtils::stripToNull).map(Double::valueOf).orElse(null);
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
