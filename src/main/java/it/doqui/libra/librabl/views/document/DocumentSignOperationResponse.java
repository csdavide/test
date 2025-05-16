package it.doqui.libra.librabl.views.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.dosign.dosign.business.session.dosign.defered.DeferedStatus;
import it.doqui.libra.librabl.views.node.ContentRef;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentSignOperationResponse {

    private SignOperationStatus status;
    private String requestId;
    private Object opaque;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ContentRef> createdContents;

    public DocumentSignOperationResponse() {
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
