package it.doqui.libra.librabl.application.model.graph;

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
