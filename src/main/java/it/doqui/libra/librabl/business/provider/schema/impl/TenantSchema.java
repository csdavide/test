package it.doqui.libra.librabl.business.provider.schema.impl;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@ToString
public class TenantSchema {
    public static final String COMMON_SCHEMA = "ANY";

    private String tenant;
    private final Map<String, CustomModelSchema> namespaces = new ConcurrentHashMap<>();
    private final BiMap<String, URI> namespaceMap = HashBiMap.create();
}
