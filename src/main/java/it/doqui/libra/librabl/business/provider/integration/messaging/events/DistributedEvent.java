package it.doqui.libra.librabl.business.provider.integration.messaging.events;

import it.doqui.libra.librabl.foundation.TenantRef;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
public class DistributedEvent {
    private String type;
    private String id;
    private String correlationId;
    private TenantRef tenantRef;
    private String sender;
    private Map<String, Object> data;
}
