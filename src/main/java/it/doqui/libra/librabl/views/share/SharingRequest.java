package it.doqui.libra.librabl.views.share;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
public class SharingRequest {
    private String contentPropertyName;
    private String source;
    private String filePropertyName;
    private String disposition;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
}
