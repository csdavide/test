package it.doqui.libra.librabl.business.provider.mappers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import it.doqui.libra.librabl.business.provider.data.entities.*;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.foundation.Localizable;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.views.association.AssociationItem;
import it.doqui.libra.librabl.views.association.LinkItem;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.*;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.views.schema.TypeDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static it.doqui.libra.librabl.business.provider.mappers.PropertyConverter.TYPE_DATE;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_MODIFIED;
import static it.doqui.libra.librabl.views.node.MapOption.*;

@ApplicationScoped
@Slf4j
public class NodeMapper {

    @Inject
    ModelManager modelManager;

    @Inject
    PropertyConverter propertyConverter;

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

            n.getContents().addAll(node.getData().getContents());
            n.getAspects().addAll(node.getData().getAspects());

            if (!optionSet.contains(NO_PROPERTIES)) {
                node.getData().getProperties().forEach((k,v) -> {
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
                                if (StringUtils.equals(pd.getType(), TYPE_DATE) && !s.contains("T")) {
                                    v = Optional.ofNullable(DateISO8601Utils.parseAsZonedDateTime(s)).map(d -> d.format(DateISO8601Utils.dateFormat)).orElse(null);
                                }
                            }
                        }

                        if (v != null || !optionSet.contains(NO_NULL_PROPERTIES)) {
                            n.getProperties().put(k, v);
                        }
                    }
                });

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
                    : n.getAspects().stream().anyMatch(s -> StringUtils.equals(s, Constants.ASPECT_SYS_ARCHIVED)) ? ArchivedStatus.ORPHAN
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
        y.setHard(x.isHard());
        return y;
    }
}
