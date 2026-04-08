package it.doqui.libra.librabl.infrastructure.configuration;

import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.ports.out.ConfigurationRepository;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.domain.model.tenant.TenantLimit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ConfigurationManager implements ConfigurationRepository {

    @Inject
    TenantRepository tenantRepository;

    @Inject
    SessionContext sessionContext;

    @Override
    public String getStringProperty(String tenant, String name, boolean defaultLimitValueRequired) {
        return tenantRepository
            .findByIdOptional(tenant)
            .map(TenantSpace::getData)
            .map(TenantData::getProperties)
            .map(p -> p.get(name))
            .map(Object::toString)
            .orElse(ConfigProvider.getConfig().getOptionalValue(name, String.class).orElse(defaultLimitValueRequired ? getDefaultValue(name) : null));
    }

    @Override
    public String getStringProperty(String name, boolean defaultLimitValueRequired) {
        return getStringProperty(sessionContext.getTenant(), name, defaultLimitValueRequired);
    }

    @Override
    public Integer getIntegerProperty(String name, boolean defaultLimitValueRequired) {
        return Optional.ofNullable(getStringProperty(name, defaultLimitValueRequired)).map(Integer::parseInt).orElse(null);
    }

    @Override
    public boolean getBooleanProperty(String name) {
        return Optional.ofNullable(getStringProperty(name, false)).map(Boolean::parseBoolean).orElse(false);
    }

    @Override
    public int getLimit(TenantLimit.Operation operation, SessionMode mode, TenantLimit.LimitFeature feature) {
        var name = String.format("libra.%s.%s.limit",
                Optional.ofNullable(operation).map(Enum::name).map(String::toLowerCase).orElseThrow(IllegalArgumentException::new),
                Optional.ofNullable(mode).map(Enum::name).map(String::toLowerCase).orElse("sync"));
        if (operation.equals(TenantLimit.Operation.DELETE)) {
            name = name.concat("." + Optional.ofNullable(feature).map(Enum::name).map(String::toLowerCase).orElse("default"));
        }

        var result = getIntegerProperty(name, true);
        return result == null ? Integer.MAX_VALUE : result;
    }

    @Override
    public List<String> getAllLimitPropertyKeys() {
        return List.of(
            "libra.delete.sync.limit.default",
            "libra.delete.async.limit.default",
            "libra.delete.sync.limit.higher",
            "libra.delete.async.limit.higher",
            "libra.rename.sync.limit",
            "libra.rename.async.limit",
            "libra.link.sync.limit",
            "libra.link.async.limit"
        );
    }

    @Override
    public TenantLimit mapToTenantLimit(String key, Integer value) {
        var propertyParts = key.split("\\.");
        var operation = TenantLimit.Operation.valueOf(
            Optional.ofNullable(propertyParts[1])
                .map(String::toUpperCase)
                .orElseThrow(IllegalArgumentException::new)
        );
        var mode = SessionMode.valueOf(
            Optional.ofNullable(propertyParts[2])
                .orElse("sync")
                .toUpperCase()
        );

        String feature = "";
        if (propertyParts.length > 4) {
            feature = Optional.ofNullable(propertyParts[4])
                .orElse(operation.equals(TenantLimit.Operation.DELETE) ? "default" : "")
                .toUpperCase();
        }

        return new TenantLimit(operation, mode,
            value == null ? Integer.parseInt(Optional.ofNullable(getDefaultValue(key)).orElse(String.valueOf(Integer.MAX_VALUE))) : value,
            setFeature(value, feature));
    }

    @Override
    public String mapToConfigPropertyKey(TenantLimit tenantLimit) {
        if (tenantLimit == null) {
            return null;
        }

        var operation = String.valueOf(tenantLimit.getOperation()).toLowerCase();
        var mode = String.valueOf(tenantLimit.getMode()).toLowerCase();
        var feature = getFeature(tenantLimit.getFeatures(), tenantLimit.getOperation());

        var limitKey = String.format("libra.%s.%s.limit", operation, mode);
        return limitKey + (!feature.isEmpty() ? "." + feature : "");
    }

    private String getDefaultValue(String key) {
        return switch (key) {
            case "libra.delete.sync.limit.default" -> "1000";
            case "libra.delete.async.limit.default" -> "5000";
            case "libra.delete.sync.limit.higher", "libra.rename.sync.limit", "libra.link.sync.limit" -> "10000";
            case "libra.delete.async.limit.higher", "libra.link.async.limit", "libra.rename.async.limit" -> "100000";
            default -> null;
        };
    }

    private Set<TenantLimit.LimitFeature> setFeature(Integer value, String feature) {
        var res = new HashSet<TenantLimit.LimitFeature>();
        if (value == null) {
            res.add(TenantLimit.LimitFeature.DEFAULT);
        }
        if (Strings.CI.equals(feature, "higher")) {
            res.add(TenantLimit.LimitFeature.HIGHER);
        }
        return res;
    }

    private String getFeature(Set<TenantLimit.LimitFeature> features, TenantLimit.Operation operation) {
        if (operation.equals(TenantLimit.Operation.DELETE)) {
            if (features.contains(TenantLimit.LimitFeature.HIGHER)) {
                return "higher";
            } else {
                return "default";
            }
        } else {
            return "";
        }
    }
}
