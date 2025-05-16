package it.doqui.libra.librabl.api.v1.cxf.mappers;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.libra.librabl.foundation.Localizable;
import it.doqui.libra.librabl.views.association.LinkItem;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.document.FileFormatDescriptor;
import it.doqui.libra.librabl.views.node.InputIdentifiedNodeRequest;
import it.doqui.libra.librabl.views.node.InputNodeRequest;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import it.doqui.libra.librabl.views.node.NodeItem;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ContentMapper {

    private void fill(InputNodeRequest n, Content c) {
        n.setTypeName(c.getTypePrefixedName());
        if (c.getAspects() != null) {
            for (Aspect a : c.getAspects()) {
                n.getAspects().add(a.getPrefixedName());
                fill(n, a.getProperties());
            }
        }

        fill(n, c.getProperties());
    }

    private void fill(InputNodeRequest n, Property[] properties) {
        if (properties != null) {
            for (Property p : properties) {
                Object value = null;
                if (p.getValues() != null) {
                    if (p.isMultivalue()) {
                        value = Arrays.stream(p.getValues()).collect(Collectors.toList());
                    } else if (p.getValues().length > 0) {
                        if (p.getValues()[0] == null) {
                            value = Optional.empty();
                        } else {
                            value = p.getValues()[0];
                        }
                    }
                }
                n.getProperties().put(p.getPrefixedName(), value);
            }
        }
    }

    public InputIdentifiedNodeRequest asInputIdentifiedNodeRequest(Content c) {
        return asInputNodeRequest(c, InputIdentifiedNodeRequest.class);
    }

    public <T extends InputNodeRequest> T asInputNodeRequest(Content c, Class<T> clazz) {
        try {
            T n = clazz.getConstructor().newInstance();
            fill(n, c);
            if (n instanceof InputIdentifiedNodeRequest m) {
                m.setUuid(c.getUid());
            } else if (n instanceof LinkedInputNodeRequest m) {
                var a = new LinkItemRequest();
                a.setTypeName(c.getParentAssocTypePrefixedName());
                a.setName(c.getPrefixedName());
                a.setRelationship(RelationshipKind.PARENT);
                a.setTypeName(c.getParentAssocTypePrefixedName());
                a.setHard(true);

                m.getAssociations().add(a);
            }

            return n;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Content asContent(NodeItem n) {
        return asContent(n, null);
    }

    public Content asContent(NodeItem n, List<String> filterPropertyNames) {
        Content c = new Content();
        c.setUid(n.getUuid());
        c.setTypePrefixedName(n.getTypeName());
        c.setModelPrefixedName(n.getModelName());

        n.getContents().stream().findFirst().ifPresent(cm -> {
            c.setSize(Optional.ofNullable(cm.getSize()).orElse(0L));
            c.setMimeType(cm.getMimetype());
            c.setEncoding(cm.getEncoding());
            c.setContentPropertyPrefixedName(cm.getName());
        });

        // properties
        final List<Property> properties;
        if (filterPropertyNames == null) {
            properties = n.getProperties().entrySet().stream()
                .map(entry -> map(entry.getKey(), entry.getValue()))
                .toList();
        } else {
            properties = new ArrayList<>(filterPropertyNames.size());
            for (var name : filterPropertyNames) {
                var value = n.getProperties().get(name);
                properties.add(map(name, value));
            }
        }

        c.setProperties(properties.toArray(new Property[0]));

        // aspects
        List<Aspect> aspects = n.getAspects().stream()
            .map(a -> new Aspect(a, null))
            .toList();
        c.setAspects(aspects.toArray(new Aspect[0]));

        // primary parent
        n.getParents()
            .stream()
            .filter(LinkItem::isHard)
            .findFirst()
            .ifPresent(p -> {
                c.setPrefixedName(p.getName());
                c.setParentAssocTypePrefixedName(p.getTypeName());
            });

        return c;
    }

    private Property map(String propertyName, Object value) {
        Property p = new Property();
        p.setPrefixedName(propertyName);

        if (value == null) {
            p.setValues(null);
        } else {
            Locale locale = Locale.getDefault();
            List<String> values = new ArrayList<>();
            if (value instanceof Collection<?> collection) {
                p.setMultivalue(true);
                for (Object obj : collection) {
                    fillPropertyValue(locale, values, obj);
                }
            } else {
                fillPropertyValue(locale, values, value);
            }

            p.setValues(values.toArray(new String[0]));
        }

        return p;
    }

    private void fillPropertyValue(Locale locale, List<String> values, Object obj) {
        if (obj != null) {
            if (obj instanceof Localizable localizable) {
                Object localizedObj = localizable.getLocalizedValue(locale);
                if (localizedObj != null) {
                    values.add(localizedObj.toString());
                }
            } else {
                values.add(obj.toString());
            }
        }
    }

    public FileFormatInfo asFileFormatInfo(FileFormatDescriptor ff, FileFormatDescriptor.FileFormatItem fi) {
        var r = new FileFormatInfo();
        r.setIdentificationDate(new Date(ff.getIdentifiedAt().toInstant().toEpochMilli()));
        switch (fi.getResult()) {
            case POSITIVE_SPECIFIC:
                r.setTypeDescription("positive-specific");
                r.setTypeCode(10);
                break;

            case POSITIVE_GENERIC:
                r.setTypeDescription("positive-generic");
                r.setTypeCode(11);
                break;

            case TENTATIVE:
                r.setTypeDescription("tentative");
                r.setTypeCode(12);
                break;

            default:
                r.setTypeCode(0);
                r.setTypeDescription("negative");
                break;
        }

        r.setDescription(fi.getName());
        r.setFormatVersion(fi.getFormatVersion());
        r.setPuid(fi.getPuid());
        r.setWarning(fi.getFormatIdentifier().name());
        r.setTypeExtension(fi.getExtensions() != null ? fi.getExtensions().stream().findFirst().orElse(null) : null);
        r.setMimeType(asCommaSeparatedValue(fi));

        return r;
    }

    public String asCommaSeparatedValue(FileFormatDescriptor.FileFormatItem fi) {
        if (fi.getMimeTypes() != null && !fi.getMimeTypes().isEmpty()) {
            var sb = new StringBuilder();
            for (int i = 0; i < fi.getMimeTypes().size(); i++) {
                if (i + 1 == fi.getMimeTypes().size()) {
                    sb.append(fi.getMimeTypes().get(i));
                } else {
                    sb.append(fi.getMimeTypes().get(i)).append(", ");
                }
            }
            return sb.toString();
        } else {
            return null;
        }
    }

    public FileFormatInfo[] asFileFormatInfoArray(FileFormatDescriptor ff) {
        return ff.getItems()
                .stream()
                .map(fi -> asFileFormatInfo(ff, fi))
                .toList()
                .toArray(new FileFormatInfo[0]);
    }

    public FileReport asFileReport(FileFormatDescriptor ff) {
        var result = new FileReport();
        result.setFormats(asFileFormatInfoArray(ff));

        var pwdProtectedAnalyzers = ff.getFileProperties().get(FileFormatDescriptor.FileProperty.PASSWORD_PROTECTED);
        var damagedAnalyzers = ff.getFileProperties().get(FileFormatDescriptor.FileProperty.DAMAGED);

        result.setPasswordProtected(pwdProtectedAnalyzers != null && pwdProtectedAnalyzers.size() == ff.getPdfAnalyzers().size());
        result.setDamaged(damagedAnalyzers != null && damagedAnalyzers.size() == ff.getPdfAnalyzers().size());

        result.setSignatures(isPdfAnalysisValid(ff) ? ff.getSignaturesNumber() : -1);
        return result;
    }

    private boolean isPdfAnalysisValid(FileFormatDescriptor ff) {
        var values = ff.getFileProperties().entrySet().stream()
            .filter(fp -> fp.getKey().equals(FileFormatDescriptor.FileProperty.DAMAGED) || fp.getKey().equals(FileFormatDescriptor.FileProperty.PASSWORD_PROTECTED))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).values();
        for (var value : values) {
            if (!value.isEmpty() && value.size() != ff.getPdfAnalyzers().size()) {
                return false;
            }
        }
        return true;
    }
}
