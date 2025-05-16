package it.doqui.libra.librabl.business.service.core;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class ApplicationTransaction {
    private Long id;
    private String tenant;
    private String uuid;
    private ZonedDateTime createdAt;
    private ZonedDateTime indexedAt;
    private String dbSchema;
}
