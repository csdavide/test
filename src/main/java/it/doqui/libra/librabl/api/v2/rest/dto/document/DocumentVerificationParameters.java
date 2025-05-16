package it.doqui.libra.librabl.api.v2.rest.dto.document;

import it.doqui.libra.librabl.views.OperationMode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
public class DocumentVerificationParameters {
    private ZonedDateTime verificationDateTime;

    @Parameter(description = "Operation mode", schema = @Schema(implementation = String.class, enumeration = {"SYNC","ASYNC","AUTO"}))
    private OperationMode mode;

    @Parameter(description = "Sync operation timeout (in seconds)", schema = @Schema(implementation = Long.class))
    private Long timeout;
}
