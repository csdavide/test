package it.doqui.libra.librabl.views.node;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public class ContentRequest {
    private String uuid;
    private String contentPropertyName;
}
