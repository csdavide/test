package it.doqui.libra.librabl.application.model.properties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import it.doqui.libra.librabl.foundation.serialization.UriWithEncodingDeserializer;
import it.doqui.libra.librabl.domain.model.files.ContentBasicDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.net.URI;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = ContentBasicDescriptor.class)
public final class ExternalContentDescriptor extends ContentBasicDescriptor implements PropertyObject {
    private ExternalSource source;

    @JsonSetter(nulls = Nulls.SKIP)
    private ContentDestination target = ContentDestination.FILE;

    @JsonSetter(nulls = Nulls.SKIP)
    private ConverterType converter = ConverterType.IDENTITY;

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExternalSource {
        private ContentRef ref;

        @JsonDeserialize(using = UriWithEncodingDeserializer.class)
        private URI uri;
    }

}

