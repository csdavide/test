package it.doqui.libra.librabl.views.share;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.views.Location;
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
}
