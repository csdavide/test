package it.doqui.libra.librabl.application.mappers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import it.doqui.libra.librabl.application.model.graph.ArchivedNodeItem;
import it.doqui.libra.librabl.application.model.graph.ArchivedStatus;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import it.doqui.libra.librabl.application.model.graph.NodePathItem;
import it.doqui.libra.librabl.application.service.SharedLinkHandler;
import it.doqui.libra.librabl.domain.model.graph.Constants;
import it.doqui.libra.librabl.domain.model.files.ContentProperty;
import it.doqui.libra.librabl.domain.model.files.FileMetadata;
import it.doqui.libra.librabl.domain.model.graph.GraphNode;
import it.doqui.libra.librabl.domain.model.graph.PropertyProvider;
import it.doqui.libra.librabl.domain.model.schema.CustomModelSchema;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.model.schema.TypeDescriptor;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.service.MimeTypeService;
import it.doqui.libra.librabl.foundation.Localizable;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.*;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.association.AssociationItem;
import it.doqui.libra.librabl.application.model.association.LinkItem;
import it.doqui.libra.librabl.application.model.association.RelationshipKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.util.*;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.TYPE_DATE;
import static it.doqui.libra.librabl.domain.policy.MapOption.*;

@ApplicationScoped
@Slf4j
public class NodeMapper {

    @Inject
    ModelManagerPort modelManager;

    @Inject
    PropertyConverter propertyConverter;

    @Inject
    SharedLinkHandler sharedLinkHandler;

    @Inject
    MimeTypeService mimeTypeService;

    private <T extends NodeItem> T asNodeItem(GraphNode node, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale, Class<T> clazz) {
        try {
            var n = clazz.getDeclaredConstructor().newInstance();
            n.setId(node.getId());
            n.setTenant(node.getTenant());
            n.setUuid(node.getUuid());
            n.setTypeName(node.getTypeName());

            var schema = modelManager.getContextModel();
            TypeDescriptor type = schema.getType(node.getTypeName());
            if (type != null) {
                CustomModelSchema model = schema.getNamespaceSchema(type.getName());
                if (model != null) {
                    n.setModelName(model.getModelName());
                }
            }

            n.getAspects().addAll(node.getAspects());

            if (optionSet.contains(DOWNLOAD_LINKS)) {
                node.getContents().stream().map(fd -> {
                    if (fd.getContentUrl() == null) {
                        return toContentProperty(fd, node);
                    } else {
                        var downloadUrl = sharedLinkHandler.generateSignedLink(node.getUuid(), fd.getContentUrl());
                        var result = toContentProperty(fd, node);
                        result.setDownloadUrl(downloadUrl);
                        return result;
                    }
                }).forEach(n.getContents()::add);
            } else {
                n.getContents().addAll(node.getContents().stream().map(fm -> toContentProperty(fm, node)).toList());
            }

            if (optionSet.contains(SHARED_LINKS)) {
                n.getSharedLinks().addAll(sharedLinkHandler.listSharingItems(node));
            }

            if (!optionSet.contains(NO_PROPERTIES)) {
                node.getProperties().entrySet().stream()
                        .map(entry -> mapProperty(entry.getKey(), entry.getValue(), schema, optionSet, filterPropertyNames, locale))
                        .filter(Objects::nonNull)
                        .forEach(entry -> n.getProperties().put(entry.key, entry.value));

                if (optionSet.contains(EXTERNAL_PROPERTIES) && node instanceof ActiveNode an) {
                    an.getExternalProperties().entrySet().stream()
                            .map(entry -> mapProperty(entry.getKey(), entry.getValue(), schema, optionSet, filterPropertyNames, locale))
                            .filter(Objects::nonNull)
                            .forEach(entry -> n.getProperties().put(entry.key, entry.value));
                }

                if (optionSet.contains(LEGACY)) {
                    if (n.getAspects().contains(Constants.ASPECT_CM_VERSIONABLE)) {
                        if (filterPropertyNames == null || filterPropertyNames.isEmpty() || filterPropertyNames.contains(Constants.CM_VERSION_LABEL)) {
                            n.getProperties().put(Constants.CM_VERSION_LABEL, String.format("1.%d", node.getVersion()));
                        }
                    }
                }
            }

            // add system properties
            if (optionSet.contains(SYS_PROPERTIES) || (filterPropertyNames != null && !filterPropertyNames.isEmpty())) {
                final Map<String,Object> sysProperties = new HashMap<>();
                sysProperties.put("sys:node-dbid", node.getId());
                sysProperties.put("sys:node-uuid", node.getUuid());
                sysProperties.put("sys:store-protocol", "workspace");
                sysProperties.put("sys:store-identifier", String.format("@%s@SpacesStore", n.getTenant()));
                sysProperties.put("ecm-sys:version", node.getVersion());
                sysProperties.put("ecm-sys:dataModifica", n.getProperties().get(CM_MODIFIED));

                Multimap<String,String> contentMultiMap = ArrayListMultimap.create();
                for (var cp : n.getContents()) {
                    contentMultiMap.put(cp.getName(), optionSet.contains(LEGACY) ? cp.toLegacyString() : cp.toString());
                }
                for (var contentPropertyName : contentMultiMap.keySet()) {
                    var values = contentMultiMap.get(contentPropertyName);
                    if (values.size() == 1) {
                        sysProperties.put(contentPropertyName, values.stream().findFirst().orElse(null));
                    } else {
                        sysProperties.put(contentPropertyName, values);
                    }
                }

                var includeAllProperties = filterPropertyNames == null || filterPropertyNames.isEmpty();
                sysProperties.forEach((k,v) -> {
                    if (includeAllProperties || filterPropertyNames.contains(k)) {
                        n.getProperties().put(k, v);
                    }
                });
            }

            if (n.isPublic()) {
                n.getProperties().put(PROP_ECMSYS_PUBLIC_LINK, sharedLinkHandler.generatePublicLink(n.getUuid(), "public", null));
            }

            if (optionSet.contains(VARRAY)) {
                var map = new HashMap<String,Object>(n.getProperties().size());
                n.getProperties().forEach((k,v) -> {
                    if (v == null) {
                        map.put(k, null);
                    } else {
                        var r = new ArrayList<String>();
                        if (v instanceof Collection<?> collection) {
                            for (var x : collection) {
                                r.add(x == null ? null : x.toString());
                            }
                        } else {
                            r.add(v.toString());
                        }
                        map.put(k, r);
                    }
                });

                n.getProperties().clear();
                n.getProperties().putAll(map);
            }

            return n;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private PropertyEntry mapProperty(String k, Object v, ModelSchema schema, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        if (filterPropertyNames == null || filterPropertyNames.isEmpty() || filterPropertyNames.contains(k)) {
            if (v instanceof Map) {
                v = propertyConverter.convertPropertyValue(schema.getProperty(k), v);
                if (locale != null && v instanceof Localizable localizable) {
                    log.debug("Using locale {}", locale);
                    v = localizable.getLocalizedValue(locale);
                }
            } else if (optionSet.contains(LEGACY) && v instanceof String s) {
                var pd = schema.getProperty(k);
                if (pd != null) {
                    if (Strings.CS.equals(pd.getType(), TYPE_DATE) && !s.contains("T")) {
                        v = Optional.ofNullable(DateISO8601Utils.parseAsZonedDateTime(s)).map(d -> d.format(DateISO8601Utils.dateFormat)).orElse(null);
                    }
                }
            }

            if (v != null || !optionSet.contains(NO_NULL_PROPERTIES)) {
                return new PropertyEntry(k, v);
            }
        }

        return null;
    }

    private record PropertyEntry(String key, Object value) {}

    public ArchivedNodeItem asNodeItem(ArchivedNode node, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        var n = asNodeItem(node, optionSet, filterPropertyNames, locale, ArchivedNodeItem.class);

        if (optionSet.contains(PARENT_ASSOCIATIONS) || optionSet.contains(PARENT_HARD_ASSOCIATIONS)) {
            n.getParents().addAll(
                node.getParents()
                    .stream()
                    .filter(a -> optionSet.contains(PARENT_ASSOCIATIONS) || a.isHard())
                    .map(this::mapArchivedParent)
                    .toList()
            );
            n.setStatus(
                n.getParents().stream().anyMatch(LinkItem.ArchivedLinkItem::isActive) ? ArchivedStatus.DELETED
                    : n.getAspects().stream().anyMatch(s -> Strings.CS.equals(s, Constants.ASPECT_SYS_ARCHIVED)) ? ArchivedStatus.ORPHAN
                        : ArchivedStatus.DESCENDANT
            );
        }

        return n;
    }

    public NodeItem asNodeItem(ActiveNode node, Set<MapOption> optionSet, Set<String> filterPropertyNames, Locale locale) {
        var n = asNodeItem(node, optionSet, filterPropertyNames, locale, NodeItem.class);

        if (optionSet.contains(PARENT_ASSOCIATIONS) || optionSet.contains(PARENT_HARD_ASSOCIATIONS)) {
            n.getParents().addAll(
                node.getParents()
                    .stream()
                    .filter(a -> optionSet.contains(PARENT_ASSOCIATIONS) || a.isHard())
                    .map(this::mapParent)
                    .toList()
            );
        }

        if (optionSet.contains(PATHS)) {
            n.getPaths().addAll(
                node.getPaths()
                    .stream()
                    .map(this::map)
                    .toList()
            );
        }

        if (optionSet.contains(SG)) {
            var sg = node.getSecurityGroup();
            if (sg == null) {
                n.setUnmanagedSgID(Optional.empty());
            } else if (!sg.isManaged()) {
                n.setUnmanagedSgID(Optional.of(sg.getUuid()));
            }
        }

        if (optionSet.contains(TX)) {
            var txInfo = new NodeItem.TransactionInfo();
            txInfo.setId(node.getTx().getId());
            n.setTx(txInfo);
        }

        return n;
    }

    public LinkItem.ArchivedLinkItem mapArchivedParent(ArchivedAssociation association) {
        var r = new LinkItem.ArchivedLinkItem();
        r.setRelationship(RelationshipKind.PARENT);
        if (association.getArchivedParentUuid() != null) {
            r.setVertexUUID(association.getArchivedParentUuid());
            r.setActive(false);
        } else if (association.getActiveParentUuid() != null) {
            r.setVertexUUID(association.getActiveParentUuid());
            r.setActive(true);
        }
        r.setTypeName(association.getTypeName());
        r.setName(association.getName());
        r.setHard(association.isHard());
        return r;
    }

    public LinkItem mapParent(Association association) {
        var r = new LinkItem();
        r.setRelationship(RelationshipKind.PARENT);
        r.setVertexUUID(association.getParent().getUuid());
        r.setTypeName(association.getTypeName());
        r.setName(association.getName());
        r.setHard(association.isHard());
        return r;
    }

    public AssociationItem map(Association a) {
        var r = new AssociationItem();
        r.setId(a.getId());
        r.setName(a.getName());
        r.setTypeName(a.getTypeName());
        r.setHard(a.getHard());
        r.setParent(a.getParent().getUuid());
        r.setChild(a.getChild().getUuid());
        return r;
    }

    public NodePathItem map(NodePath x) {
        NodePathItem y = new NodePathItem();
        y.setPath(
            (!x.getFilePath().equals("/") && x.getFilePath().endsWith("/"))
                ? x.getFilePath().substring(0, x.getFilePath().length() - 1)
                : x.getFilePath()
        );
        y.setRoute(x.getPath());
        y.setHard(x.isHard());
        return y;
    }

    public ContentProperty toContentProperty(FileMetadata f, PropertyProvider data) {
        var filename = filename(data, f);
        var mimetype = Optional.ofNullable(f.getMimetype()).orElseGet(() -> mimeTypeService.getMimeType(filename));
        var cp = new ContentProperty();
        cp.setName(f.getName());
        cp.setMimetype(mimetype);
        cp.setEncoding(f.getEncoding());
        cp.setLocale(f.getLocale());
        cp.setFileName(filename);
        cp.setOpaque(f.isOpaque());
        cp.setSize(f.getSize());
        cp.setContentUrl(f.getContentUrl());
        cp.setHash(f.getHash());
        cp.getSigns().addAll(f.getSigns());
        return cp;
    }

    public FileData toFileData(ContentProperty cp) {
        var fileData = new FileData();
        fileData.setName(cp.getName());
        fileData.setMimetype(cp.getMimetype());
        fileData.setEncoding(cp.getEncoding());
        fileData.setLocale(Optional.ofNullable(cp.getLocale()).map(Locale::toString).orElse(null));
        fileData.setFileName(cp.getFileName());
        fileData.setOpaque(cp.isOpaque());
        fileData.setSize(cp.getSize());
        fileData.setHash(cp.getHash());
        fileData.setContentUrl(cp.getContentUrl());
        fileData.getSigns().addAll(cp.getSigns());
        return fileData;
    }

    private String filename(PropertyProvider data, FileMetadata cp) {
        var fileName = cp.getFileName();
        if (fileName == null) {
            fileName = ObjectUtils.getAsString(data.getProperty(CM_FILENAME));
            if (fileName == null) {
                fileName = ObjectUtils.getAsString(data.getProperty(CM_NAME));
            }
        }

        return mimeTypeService.filename(fileName, cp.getMimetype(), Set.of());
    }
}
