package it.doqui.libra.librabl.application.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.domain.model.files.ContentMetadataProvider;
import it.doqui.libra.librabl.domain.model.files.ContentProperty;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.document.SignData;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SignedContentStream extends ContentStream implements ContentMetadataProvider {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<SignData> signs;

    public SignedContentStream() {
        this.signs = new ArrayList<>();
    }

    @Override
    public void addMetadata(ContentProperty cp) {
        cp.getSigns().addAll(signs);
    }
}
