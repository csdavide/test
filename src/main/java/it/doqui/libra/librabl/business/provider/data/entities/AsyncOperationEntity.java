package it.doqui.libra.librabl.business.provider.data.entities;

import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class AsyncOperationEntity {

    private String id;
    private String tenant;
    private AsyncOperation.Status status;
    private final Map<String,Object> data;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public AsyncOperationEntity() {
        this.data = new HashMap<>();
    }
}
