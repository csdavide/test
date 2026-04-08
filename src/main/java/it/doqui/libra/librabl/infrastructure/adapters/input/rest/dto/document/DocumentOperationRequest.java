package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document;

import it.doqui.libra.librabl.foundation.OperationMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentOperationRequest {

    @Parameter(description = "It specifies the content in which execute the operation represented by the uuid", schema = @Schema(implementation = ContentRef.class))
    private ContentRef document;

    @Parameter(description = "Operation mode", schema = @Schema(implementation = String.class, enumeration = {"SYNC","ASYNC","AUTO"}))
    private OperationMode mode = OperationMode.SYNC;

    @Parameter(description = "Sync operation timeout (in seconds)", schema = @Schema(implementation = Long.class))
    private Long timeout;
}
