package it.doqui.libra.librabl.application.model.properties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "kind", // discriminator
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PropertyValueOperation.class, name = "valueOperation"),
        @JsonSubTypes.Type(value = ExternalContentDescriptor.class, name = "externalContent"),
        @JsonSubTypes.Type(value = ContentPropertyObject.class, name = "content"),
        @JsonSubTypes.Type(value = BufferDescriptor.class, name = "buffer"),
        @JsonSubTypes.Type(value = MLTextProperty.class, name = "text")
})
public sealed interface PropertyObject permits BufferDescriptor, ContentPropertyObject, ExternalContentDescriptor, MLTextProperty, PropertyValueOperation {}
