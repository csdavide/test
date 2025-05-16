package it.doqui.libra.librabl.views.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DigestResponse {

    private final String alg;
    private final String digest;

    public DigestResponse(String algorithm, String digest) {
        this.alg = algorithm;
        this.digest = digest;
    }
}
