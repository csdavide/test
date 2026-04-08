package it.doqui.libra.librabl.infrastructure.adapters.output.droid;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class FormatInfo {
    private String puid;
    private String mimeType;
    private String version;
    private String name;
    private String extension;
}
