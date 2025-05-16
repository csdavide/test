package it.doqui.libra.librabl.views.node;

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
}
