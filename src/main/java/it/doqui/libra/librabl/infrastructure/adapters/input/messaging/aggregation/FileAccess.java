package it.doqui.libra.librabl.infrastructure.adapters.input.messaging.aggregation;

import java.net.URI;
import java.util.UUID;

public record FileAccess(UUID uuid, URI fileUri, String usageToken) { }
