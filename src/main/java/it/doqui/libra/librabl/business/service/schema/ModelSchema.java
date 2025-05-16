package it.doqui.libra.librabl.business.service.schema;

import it.doqui.libra.librabl.views.schema.*;

import java.net.URI;
import java.util.Collection;
import java.util.List;

public interface ModelSchema {

    CustomModelSchema getModel(String modelName);
    List<String> listNamespaceNames();
    CustomModelSchema getNamespaceSchema(final String name);
    String getNamespace(final URI uri);
    PropertyDescriptor getProperty(String name);
    TypeDescriptor getType(String name);
    AspectDescriptor getAspect(String name);
    ConstraintDescriptor getConstraint(String name);
    AssociationDescriptor getAssociation(String name);
    Collection<AssociationDescriptor> getParentTypeAssociations(String parentType);
    List<AspectDescriptor> getAspectHierarchy(String name);
    List<TypeDescriptor> getTypeHierarchy(String name);
    TypeDescriptor getFlatType(String name, Collection<String> aspects);
    AspectDescriptor getFlatAspect(String name);
}
