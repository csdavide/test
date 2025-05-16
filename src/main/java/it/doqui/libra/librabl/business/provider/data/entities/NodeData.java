package it.doqui.libra.librabl.business.provider.data.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.views.node.ContentProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeData implements Serializable {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ContentProperty> contents;

    private final Set<String> aspects;

    private final Map<String, Object> properties;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> internals;

    public NodeData() {
        this.aspects = new HashSet<>();
        this.properties = new HashMap<>();
        this.contents = new ArrayList<>();
    }

    public void copyFrom(NodeData data) {
        this.getContents().addAll(data.getContents());
        this.getAspects().addAll(data.getAspects());
        this.getProperties().putAll(data.getProperties());
    }

    public void replaceWith(NodeData data) {
        this.aspects.clear();
        this.properties.clear();
        this.contents.clear();

        this.aspects.addAll(data.getAspects());
        this.properties.putAll(data.getProperties());
        this.contents.addAll(data.getContents());
    }

    public void replaceAspects(Collection<String> aspects) {
        this.aspects.clear();
        this.aspects.addAll(aspects);
    }

    public ContentProperty getContentProperty(String name) {
        return getContentProperty(name, null);
    }

    public ContentProperty getContentProperty(String name, String fileName) {
        final List<ContentProperty> propertyFilteredContents;
        if (StringUtils.isBlank(name)) {
            propertyFilteredContents = contents;
        } else {
            propertyFilteredContents = contents.stream().filter(cp -> name.equals(cp.getName())).toList();
        }

        return propertyFilteredContents.stream().filter(cp -> StringUtils.isBlank(fileName) || fileName.equalsIgnoreCase(cp.getFileName())).findFirst().orElse(null);
    }

    public long countContentProperties(String name) {
        return contents.stream().filter(cp -> cp.getName().equals(name)).count();
    }

    public void removeContentProperty(ContentProperty cp) {
        this.contents.remove(cp);
    }

    public void addContentProperty(ContentProperty cp) {
        this.contents.add(cp);
    }
}
