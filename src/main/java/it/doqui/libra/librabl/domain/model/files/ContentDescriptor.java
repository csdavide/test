package it.doqui.libra.librabl.domain.model.files;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = ContentBasicDescriptor.class)
public class ContentDescriptor extends ContentBasicDescriptor implements Serializable {
    protected Long size;

    @Override
    public <T extends ContentBasicDescriptor> void mergeWith(T d) {
        super.mergeWith(d);
        if (d instanceof ContentDescriptor c) {
            if (this.size == null) {
                this.size = c.size;
            }
        }
    }
}
