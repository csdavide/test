package it.doqui.libra.librabl.application.model.ingest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString
public class ImportStreamParameters {
    private String csvSeparator;
    private int skip;
    private int limit;
    private int blockSize;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<OperationOption> options;

    public ImportStreamParameters() {
        this.options = new HashSet<>();
    }
}
