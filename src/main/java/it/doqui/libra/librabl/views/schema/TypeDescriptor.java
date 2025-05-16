package it.doqui.libra.librabl.views.schema;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(allOf = TypedInterfaceDescriptor.class)
public class TypeDescriptor extends TypedInterfaceDescriptor {
}
