package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ExternalContentDescriptor extends ContentBasicDescriptor {
    private ExternalSource source;

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExternalSource {
        private ContentRef ref;
        private URI uri;
    }
}

