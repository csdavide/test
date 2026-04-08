package it.doqui.libra.librabl.application.model.ingest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = ImportStatement.class)
public class StreamImportStatement extends ImportStatement {
    public StreamImportStatement() {
        super("stream");
    }

    private String csvSeparator;
    private int skip;
    private int limit;
    private int blockSize;
}
