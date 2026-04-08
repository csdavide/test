package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.FileMetadata;
import it.doqui.libra.librabl.domain.model.files.FileProvider;
import it.doqui.libra.librabl.domain.model.graph.PropertyProvider;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.Serializable;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeData implements Serializable, PropertyProvider, FileProvider {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<FileData> contents;

    private final Set<String> aspects;
    private final Map<String, Object> properties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<NodeLock> locks;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> internals;

    public NodeData() {
        this.aspects = new HashSet<>();
        this.properties = new HashMap<>();
        this.contents = new ArrayList<>();
        this.locks = new ArrayList<>();
    }

    public void copyFrom(NodeData data) {
        this.getContents().addAll(data.getContents());
        this.getAspects().addAll(data.getAspects());
        this.getProperties().putAll(data.getProperties());
        this.getLocks().addAll(data.getLocks());
    }

    public void replaceWith(NodeData data) {
        this.aspects.clear();
        this.properties.clear();
        this.contents.clear();
        this.locks.clear();

        this.aspects.addAll(data.getAspects());
        this.properties.putAll(data.getProperties());
        this.contents.addAll(data.getContents());
        this.locks.addAll(data.getLocks());
    }

    public void replaceAspects(Collection<String> aspects) {
        this.aspects.clear();
        this.aspects.addAll(aspects);
    }

    public FileData getFileData(String name) {
        return getFileData(name, null);
    }

    public FileData getFileData(String name, String fileName) {
        final List<FileData> propertyFilteredContents;
        if (StringUtils.isBlank(name)) {
            propertyFilteredContents = List.copyOf(contents);
        } else {
            propertyFilteredContents = contents.stream().filter(cp -> name.equals(cp.getName())).toList();
        }

        return propertyFilteredContents.stream().filter(cp -> StringUtils.isBlank(fileName) || fileName.equalsIgnoreCase(cp.getFileName())).findFirst().orElse(null);
    }

    public FileData getFileData(URI uri) {
        var uriString = uri.toString();
        return contents.stream().filter(cp -> Strings.CS.equals(cp.getContentUrl(), uriString)).findFirst().orElse(null);
    }

    public long countFileWithPropertyName(String name) {
        return contents.stream().filter(cp -> cp.getName().equals(name)).count();
    }

    public void removeFileData(FileData cp) {
        this.contents.remove(cp);
    }

    public boolean isLocked(LockType type) {
        return isLocked(Collections.singleton(type));
    }

    public boolean isLocked(Collection<LockType> types) {
        var now = ZonedDateTime.now();
        return locks.stream()
            .filter(l -> l.expires == null || l.expires.isAfter(now))
            .anyMatch(l -> types.contains(l.getType()));
    }

    @Override
    public Object getProperty(String name) {
        return getProperties().get(name);
    }

    @Override
    public boolean hasAspect(String name) {
        return aspects.contains(name);
    }

    @Override
    public Optional<FileMetadata> getContent(URI contentUrl) {
        return Optional.ofNullable(getFileData(contentUrl));
    }

    @Override
    public Optional<FileMetadata> getContent(ContentReferenceable ref) {
        return Optional.ofNullable(getFileData(ref.getContentPropertyName(), ref.getFileName()));
    }

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NodeLock {
        private String owner;
        private LockType type;
        private ZonedDateTime expires;
    }

    public enum LockType {
        WRITE
    }
}
