package it.doqui.libra.librabl.domain.ports.out;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.net.URI;

@Getter
@ToString
@Builder
public class FileRequest {
    private URI fileURI;
    private String filename;
}
