package it.doqui.libra.librabl.application.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.dosign.dosign.business.session.dosign.defered.DeferedStatus;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = JobResult.class)
public class DocumentOperationResponse extends JobResult {

    private SignOperationStatus status;
    private String requestId;
    private Object opaque;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ContentRef> createdContents;

    public DocumentOperationResponse() {
        this.createdContents = new ArrayList<>();
    }

    public static SignOperationStatus mapStatus(DeferedStatus status) {
        return switch (status) {
            case ERROR -> SignOperationStatus.ERROR;
            case READY -> SignOperationStatus.READY;
            case EXPIRED -> SignOperationStatus.EXPIRED;
            case RUNNING -> SignOperationStatus.SUBMITTED;
        };
    }

    public enum SignOperationStatus {
        SUBMITTED,
        ERROR,
        EXPIRED,
        READY,
        SCHEDULED,
        RUNNING,
        NULL
    }
}
