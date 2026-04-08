package it.doqui.libra.librabl.domain.model.files;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.application.model.properties.ContentDestination;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.InputStream;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = ContentDescriptor.class)
@Accessors(chain = true)
public class ContentStream extends ContentDescriptor implements Streamable {

    @JsonIgnore
    private transient InputStream inputStream;

    @JsonSetter(nulls = Nulls.SKIP)
    private ContentDestination target = ContentDestination.FILE;

    @Override
    public ContentStream asStream() {
        return this;
    }
}
