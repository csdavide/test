package it.doqui.libra.librabl.application.model.share;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = SharingRequest.class)
public class SharingItem extends SharingRequest implements Location {
    private String url;

    @JsonIgnore
    private String key;
}
