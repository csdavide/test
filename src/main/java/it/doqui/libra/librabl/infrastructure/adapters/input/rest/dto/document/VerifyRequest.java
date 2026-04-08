package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document;

import it.doqui.libra.librabl.domain.model.files.ContentRef;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Schema(allOf = DocumentOperationRequest.class)
public class VerifyRequest extends DocumentOperationRequest {

    @Parameter(description = "It specifies the moment (datetime) in which the verification refers to", schema = @Schema(implementation = ZonedDateTime.class))
    private ZonedDateTime verificationDateTime;

    @Parameter(description = "It specifies the detached content to verify, represented by the uuid", schema = @Schema(implementation = ContentRef.class))
    private ContentRef detachedDocument;

}
