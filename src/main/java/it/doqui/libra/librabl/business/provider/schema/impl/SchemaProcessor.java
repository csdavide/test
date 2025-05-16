package it.doqui.libra.librabl.business.provider.schema.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.doqui.libra.librabl.business.provider.schema.ConstraintRegister;
import it.doqui.libra.librabl.business.service.schema.ModelItem;
import it.doqui.libra.librabl.views.schema.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

@ApplicationScoped
@Slf4j
public class SchemaProcessor {

    @Inject
    ConstraintRegister constraintRegister;

    @Inject
    SchemaValidator schemaValidator;

    final ObjectMapper mapper;

    private final DocumentBuilderFactory dbf;

    public SchemaProcessor() {
        mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        try {
            dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            //dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
            //dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // compliant
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    CustomModelSchema process(ModelItem m, TenantSchema commonSchema, TenantSchema tenantSchema) {
        try {
            final CustomModelSchema model;
            switch (m.getFormat()) {
                case "yaml":
                case "yml":
                    model = processYaml(m);
                    break;

                case "xml": {
                    var modelMap = new HashMap<String, CustomModelSchema>();
                    Optional.ofNullable(commonSchema).ifPresent(map -> modelMap.putAll(map.getNamespaces()));
                    Optional.ofNullable(tenantSchema).ifPresent(map -> modelMap.putAll(map.getNamespaces()));
                    log.debug("Processing xml model {}, using ns map: {}", m.getName(), modelMap.keySet());
                    model = processXml(m, modelMap);
                    break;
                }

                default:
                    log.info("Ignoring model format '{}'", m.getFormat());
                    return null;
            }

            return model;
        } catch (Exception e) {
            log.error("Failed parsing of model {} in tenant {}: {}", m.getName(), m.getTenant(), e.getMessage());
            log.error(e.getMessage(), e);
            return null;
        }
    }

    boolean validate(TenantSchema schema,  TenantSchema commonSchema, CustomModelSchema model) {
        return schemaValidator.validate(schema, commonSchema, model);
    }

    void register(TenantSchema tenantSchema, CustomModelSchema model) {
        for (ModelNamespace ns : model.getNamespaces()) {
            if (ns.getUri() != null) {
                tenantSchema.getNamespaceMap().put(ns.getPrefix(), ns.getUri());
            }

            tenantSchema.getNamespaces().put(ns.getPrefix(), model);
        }
    }

    void unregister(TenantSchema tenantSchema, String ns) {
        tenantSchema.getNamespaces().remove(ns);
        tenantSchema.getNamespaceMap().remove(ns);
    }

    private CustomModelSchema processYaml(ModelItem m) {
        log.trace("Processing model '{}'", m);

        try {
            CustomModelSchema model = mapper.readValue(m.getData(), CustomModelSchema.class);
            if (model.getNamespaces().isEmpty()) {
                throw new RuntimeException(String.format("No namespace defined in '%s'", model));
            }

            model.remap();
            return model;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private CustomModelSchema processXml(ModelItem m, final Map<String, CustomModelSchema> modelMap) {
        log.trace("Processing model '{}'", m);

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(m.getData().getBytes(StandardCharsets.UTF_8)));
            CustomModelSchema model = new CustomModelSchema();
            model.setModelName(doc.getDocumentElement().getAttribute("name"));
            model.setDescription(
                    getFirstElementByTag(doc.getDocumentElement(), "description")
                            .map(Node::getTextContent)
                            .orElse(null)
            );

            model.setVersion(
                    getFirstElementByTag(doc.getDocumentElement(), "version")
                            .map(Node::getTextContent)
                            .orElse(null)
            );

            model.setAuthor(
                    getFirstElementByTag(doc.getDocumentElement(), "author")
                            .map(Node::getTextContent)
                            .orElse(null)
            );

            model.setDefaultTokenizationType(
                getFirstElementByTag(doc.getDocumentElement(), "defaultTokenizationType")
                    .map(Node::getTextContent)
                    .orElse(null)
            );

            model.setPublished(
                    getFirstElementByTag(doc.getDocumentElement(), "published")
                            .map(Node::getTextContent)
                            .filter(StringUtils::isBlank)
                            .map(LocalDate::parse)
                            .orElse(null)
            );

            parseElements(doc.getDocumentElement(), "namespaces", "namespace", elem -> {
                try {
                    ModelNamespace ns = new ModelNamespace();
                    ns.setUri(new URI(elem.getAttribute("uri")));
                    ns.setPrefix(elem.getAttribute("prefix"));
                    model.getNamespaces().add(ns);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            parseElements(doc.getDocumentElement(), "imports", "import", elem -> model.getImports().add(elem.getAttribute("prefix")));

            parseElements(doc.getDocumentElement(), "constraints", "constraint", elem -> {
                var ctype = elem.getAttribute("type");
                var cname = elem.getAttribute("name");
                if (constraintRegister.getValidator(ctype) != null) {
                    var cd = new ConstraintDescriptor();
                    cd.setName(cname);
                    cd.setType(ctype);

                    parseElements(elem, "parameter", param -> {
                        var pname = param.getAttribute("name");
                        var ptext = param.getTextContent().trim();

                        if (StringUtils.isNotBlank(ptext)) {
                            Object value;
                            if (StringUtils.equalsIgnoreCase(ptext, "false")) {
                                value = false;
                            } else if (StringUtils.equalsIgnoreCase(ptext, "true")) {
                                value = true;
                            } else {
                                value = ptext;
                            }

                            cd.getParameters().compute(pname, (k,v) -> {
                                if (v == null) {
                                    v = new ArrayList<>();
                                }

                                v.add(value);
                                return v;
                            });
                        }
                    });

                    model.addConstraint(cd);
                } else if (StringUtils.startsWith(ctype, "org.alfresco")) {
                    log.warn("Unsupported constraint '{}' of type '{}'", cname, ctype);
                    var cd = new ConstraintDescriptor();
                    cd.setName(cname);
                    cd.setType("OK");
                    model.addConstraint(cd);
                } else if (StringUtils.isNotBlank(ctype)) {
                    throw new RuntimeException(String.format("Unknown constraint '%s' of type '%s' in model %s", cname, ctype, model.getModelName()));
                }
            });

            // parse types
            parseElements(doc.getDocumentElement(), "types", "type", elem -> {
                TypeDescriptor descriptor = parseTypedElement(elem, TypeDescriptor.class, model, modelMap);
                if (descriptor != null) {
                    model.addType(descriptor);
                }
            });

            // parse aspects
            parseElements(doc.getDocumentElement(), "aspects", "aspect", elem -> {
                AspectDescriptor descriptor = parseTypedElement(elem, AspectDescriptor.class, model, modelMap);
                if (descriptor != null) {
                    model.addAspect(descriptor);
                }
            });

            log.trace("Generated model '{}'\n{}", model.getModelName(), mapper.writeValueAsString(model));
            return model;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void parseElements(Element rootElement, String containerNodeName, String childNodeName, Consumer<Element> consumer) {
        parseElements(rootElement, containerNodeName, elem -> parseElements(elem, childNodeName, consumer));
    }

    private void parseElements(Element element, String name, Consumer<Element> consumer) {
        NodeList list = element.getElementsByTagName(name);
        for (int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            consumer.accept(elem);
        }
    }

    public <T extends TypedInterfaceDescriptor> T parseTypedElement(final Element typeElem, final Class<T> clazz, final CustomModelSchema model, final Map<String, CustomModelSchema> modelMap) {
        String name = typeElem.getAttribute("name");
        if (StringUtils.isBlank(name)) {
            return null;
        }

        try {
            final T descriptor = clazz.getDeclaredConstructor().newInstance();
            descriptor.setName(name);
            descriptor.setParent(
                    getFirstElementByTag(typeElem, "parent")
                            .map(Node::getTextContent)
                            .orElse(null)
            );

            descriptor.setTitle(
                    getFirstElementByTag(typeElem, "title")
                            .map(Node::getTextContent)
                            .orElse(null)
            );

            if (StringUtils.startsWith(descriptor.getName(), "sys:") || StringUtils.startsWith(descriptor.getName(), "ecm-sys:")) {
                descriptor.setManaged(true);
            } else {
                descriptor.setManaged(
                    getFirstElementByTag(typeElem, "managed")
                        .map(Node::getTextContent)
                        .map(Boolean::valueOf)
                        .orElse(false)
                );
            }

            descriptor.setHidden(
                getFirstElementByTag(typeElem, "hidden")
                    .map(Node::getTextContent)
                    .map(Boolean::valueOf)
                    .orElse(false)
            );

            parseElements(typeElem, "properties", "property", elem -> {
                // parse property into PropertyDescriptor
                Pair<PropertyDescriptor,Boolean> p = parseProperty(elem, model, modelMap);
                if (p.getLeft() != null) {
                    PropertyDescriptor pd = p.getLeft();
                    model.addProperty(pd);
                    if (BooleanUtils.isTrue(p.getRight())) {
                        descriptor.getMandatoryProperties().add(pd.getName());
                    } else {
                        descriptor.getSuggestedProperties().add(pd.getName());
                    }
                }
            });

            parseElements(typeElem, "mandatory-aspects", "aspect", elem -> descriptor.getMandatoryAspects().add(elem.getTextContent()));

            parseElements(typeElem, "associations", "child-association", elem -> {
                // child association
                AssociationDescriptor association = parseAssociation(elem);
                association.setParent(descriptor.getName());
                model.addAssociation(association);
            });

            parseElements(typeElem, "associations", "association", elem -> {
                // association
                AssociationDescriptor association = parseAssociation(elem);
                association.setLight(true);
                association.setParent(descriptor.getName());
                model.addAssociation(association);
            });

            return descriptor;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Pair<PropertyDescriptor,Boolean> parseProperty(Element elem, final CustomModelSchema model, final Map<String, CustomModelSchema> modelMap) {
        PropertyDescriptor p = new PropertyDescriptor();
        p.setDeclarationRequired(false);
        p.setName(elem.getAttribute("name"));
        p.setTitle(
            getFirstElementByTag(elem, "title")
                .map(Node::getTextContent)
                .orElse(null)
        );
        p.setType(
            getFirstElementByTag(elem, "type")
                .map(Node::getTextContent)
                .orElse(null)
        );
        p.setDefaultValue(
            getFirstElementByTag(elem, "default")
                .map(Node::getTextContent)
                .orElse(null)
        );

        p.setMultiple(
                getFirstElementByTag(elem, "multiple")
                        .map(Node::getTextContent)
                        .map(Boolean::valueOf)
                        .orElse(false)
        );

        p.setOpaque(
            getFirstElementByTag(elem, "opaque")
                .map(Node::getTextContent)
                .map(Boolean::valueOf)
                .orElse(false)
        );

        p.setHidden(
            getFirstElementByTag(elem, "hidden")
                .map(Node::getTextContent)
                .map(Boolean::valueOf)
                .orElse(false)
        );

        if (StringUtils.startsWith(p.getName(), "sys:")) {
            p.setImmutable(false);
            p.setManaged(true);
        } else {
            p.setImmutable(false);
            p.setManaged(
                getFirstElementByTag(elem, "protected")
                    .map(Node::getTextContent)
                    .map(Boolean::valueOf)
                    .orElse(false)
            );
        }

        getFirstElementByTag(elem, "index")
            .ifPresentOrElse(index -> {
                p.setIndexed(BooleanUtils.toBooleanDefaultIfNull(BooleanUtils.toBooleanObject(index.getAttribute("enabled")), true));
                p.setStored(BooleanUtils.toBoolean(getFirstElementByTag(elem, "stored").map(Node::getTextContent).orElse(null)));
                p.setTokenized(BooleanUtils.toBoolean(getFirstElementByTag(elem, "tokenised").map(Node::getTextContent).orElse(null)));
                p.setReverseTokenized(BooleanUtils.toBoolean(getFirstElementByTag(elem, "reverse-tokenised").map(Node::getTextContent).orElse(null)));
            }, () -> {
                p.setIndexed(true);
                p.setStored(false);
                p.setTokenized(true);
                p.setReverseTokenized(false);
            });

        boolean mandatory = getFirstElementByTag(elem, "mandatory")
                .map(Node::getTextContent)
                .map(Boolean::valueOf)
                .orElse(false);

        parseElements(elem, "constraints", "constraint", ce -> p.getConstraints().add(ce.getAttribute("ref")));

        return new ImmutablePair<>(p,mandatory);
    }

    private AssociationDescriptor parseAssociation(Element elem) {
        AssociationDescriptor descriptor = new AssociationDescriptor();
        descriptor.setName(elem.getAttribute("name"));

        descriptor.setParent(
                getFirstElementByTag(elem, "source")
                    .flatMap(x -> getFirstElementByTag(x, "class"))
                    .map(Node::getTextContent)
                    .orElse(null)
        );

        descriptor.setChild(
                getFirstElementByTag(elem, "target")
                    .flatMap(x -> getFirstElementByTag(x, "class"))
                    .map(Node::getTextContent)
                    .orElse(null)
        );

        return descriptor;
    }

    private Optional<Element> getFirstElementByTag(Element parentElem, String name) {
        NodeList list = parentElem.getElementsByTagName(name);
        return  (list.getLength() > 0) ? Optional.of((Element) list.item(0)) : Optional.empty();
    }
}
