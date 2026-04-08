package it.doqui.libra.librabl.application.model.document;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class StoreParams {

    private StoreResultMode mode;
    private String path;
    private String fileName;
    private boolean returnData = false;
}
