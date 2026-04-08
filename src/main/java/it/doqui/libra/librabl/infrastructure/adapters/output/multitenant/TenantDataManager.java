package it.doqui.libra.librabl.infrastructure.adapters.output.multitenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.platform.events.CleanCacheEvent;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.utils.SQLScriptUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@ApplicationScoped
@Slf4j
public class TenantDataManager implements TenantRepository {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @ConfigProperty(name = "libra.multitenant.additional-search-path", defaultValue = "public")
    List<String> additionalSearchPath;

    @ConfigProperty(name = "libra.multitenant.sql-resources-path", defaultValue = "./res/sql")
    String sqlResPath;

    @ConfigProperty(name = "libra.multitenant.schema-prefix", defaultValue = "libra,ecm")
    String schemaPrefix;

    @ConfigProperty(name = "libra.content-store.tenant-isolation", defaultValue = "true")
    boolean tenantIsolationEnabled;

    @ConfigProperty(name = "libra.multitenant.cache.ttl", defaultValue = "24h")
    Duration cacheTTL;

    @ConfigProperty(name = "libra.multitenant.cache.size", defaultValue = "1000")
    int cacheSize;

    @Inject
    ObjectMapper objectMapper;

    private Cache<String, TenantSpace> tenantCache;

    void onStart(@Observes CleanCacheEvent ev) {
        cleanCache();
    }

    @PostConstruct
    void init() {
        tenantCache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterWrite(cacheTTL)
                .recordStats()
                .build();
    }

    @Override
    public void cleanCache() {
        tenantCache.invalidateAll();
        log.info("Tenant cache cleaned");
    }

    @Override
    public void removeTenantFromCache(String tenant) {
        tenantCache.invalidate(StringUtils.stripToEmpty(tenant).toLowerCase());
        log.info("Tenant {} invalidated from cache", tenant);
    }

    @Override
    public List<String> listAllSchemas() {
        return DBUtils.call(ds, masterSchema, conn -> {
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("select schema_name from information_schema.schemata")) {
                var schemas = new ArrayList<String>();
                var prefixes = schemaPrefix.replace(' ', ',').split(",");
                while (rs.next()) {
                    var name = rs.getString(1);
                    if (Arrays.stream(prefixes).anyMatch(name::startsWith)) {
                        schemas.add(name);
                    }
                }
                return schemas;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public List<String> listTenantsInSchema(String schemaName) {
        return DBUtils.call(ds, masterSchema, conn -> {
            try {
                var sql = "select tenant from ecm_tenants where schema_name = ?";
                try (PreparedStatement stmt  = conn.prepareStatement(sql)) {
                    stmt.setString(1, schemaName);
                    try (var rs = stmt.executeQuery()) {
                        var tenants = new ArrayList<String>();
                        while (rs.next()) {
                            tenants.add(rs.getString("tenant"));
                        }
                        return tenants;
                    }
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void checkIfSchemaExists(String schema) {
        DBUtils.call(ds, schema, conn -> {
            try {
                if (conn.getSchema() == null) {
                    throw new NotFoundException(String.format("Schema '%s' does not exists", schema));
                }

                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("select count(*) from ecm_users")) {
                    return rs.next() ? rs.getLong(1) : 0L;
                } catch (Exception e) {
                    throw new PreconditionFailedException(String.format("No table on schema '%s'", schema));
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void initializeSchema(String schema, boolean includeFK) {
        log.info("Initializing database schema '{}' using additional search path {}", schema, additionalSearchPath);
        DBUtils.transactionCall(ds, ObjectUtils.append(schema, additionalSearchPath), conn -> {
            Consumer<Path> consumer = path -> {
                if (!includeFK && path.getFileName().toString().toLowerCase().endsWith("_fk.sql")) {
                    log.info("Ignoring fk script {}", path);
                    return;
                }

                log.info("Processing script at {} on schema {}", path, schema);
                try {
                    var sqlStatements = SQLScriptUtils.parseSQLScript(path).stream().map(s -> s.replace("{schema}", schema)).toList();
                    SQLScriptUtils.executeSQLBatches(conn, sqlStatements, 1000);
                } catch (SQLException e) {
                    throw new SystemException(e);
                }
            };

            try {
                executeScriptsInFolder(Path.of(sqlResPath, "tenant"), consumer);
                executeScriptsInFolder(Path.of(sqlResPath, "models"), consumer);
            } catch (IOException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    @Override
    public void destroySchema(String schema) {
        log.info("Destroying database schema '{}' using additional search path {}", schema, additionalSearchPath);
        executeScriptsOnSchema(schema, List.of("uninstall"));
        log.info("Database schema '{}' has been destroyed", schema);
    }

    @Override
    public void upgradeSchema(Set<String> schemaSet, int fromVersion, int targetVersion) {
        try (var conn = ds.getConnection()) {
            conn.setAutoCommit(true);
            conn.setSchema(masterSchema);

            var schemas = new HashSet<String>();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("select distinct schema_name from ecm_tenants")) {
                while (rs.next()) {
                    var name = rs.getString(1);
                    if (schemaSet.isEmpty() || schemaSet.contains(name)) {
                        schemas.add(name);
                    }
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            boolean isMasterTenant = schemas.contains(masterSchema);
            if (!isMasterTenant && schemaSet.contains(masterSchema)) {
                schemas.add(masterSchema);
            }

            for (var s : schemaSet) {
                if (!schemas.contains(s)) {
                    throw new NotFoundException(String.format("Schema '%s' not found in database", s));
                }
            }

            if (schemas.isEmpty()) {
                log.warn("No schema found in database");
                return;
            }

            for (var s : schemas) {
                log.trace("Updating database schema '{}' if required using additional search path {}", s, additionalSearchPath);
                conn.setSchema(s);
                int n = executeScriptsOnSchema(conn, s, 0, List.of("upgrade"), (scriptName) -> {
                    var parts = scriptName.split("_");
                    if (parts.length < 1) {
                        throw new IllegalArgumentException("Invalid script name: " + scriptName);
                    }

                    var version = Integer.parseInt(parts[0]);
                    if (version < fromVersion || version > targetVersion) {
                        return false;
                    }

                    var suffix = parts.length > 1 ? parts[1] : "";
                    if (Strings.CS.equals(suffix, "master")) {
                        return Strings.CS.equals(s, masterSchema);
                    }

                    return isMasterTenant || !Strings.CS.equals(s, masterSchema);
                });

                if (n > 0) {
                    log.info("Database schema '{}' has been updated to version {}", s, targetVersion);
                } else {
                    log.info("Database schema '{}' is already up to date", s);
                }
            }

            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new SystemException(e);
        }
    }

    private void executeScriptsOnSchema(String schema, List<String> folders) {
        Function<Connection,Integer> f = conn -> executeScriptsOnSchema(conn, schema, 1000, folders, (scriptName) -> true);
        DBUtils.transactionCall(ds, ObjectUtils.append(schema, additionalSearchPath), f);
    }

    private int executeScriptsOnSchema(Connection conn, String schema, int batchSize, List<String> folders, Predicate<String> predicate) {
        AtomicInteger count = new AtomicInteger();
        Consumer<Path> consumer = path -> {
            log.info("Processing script at {} on schema '{}' using additional search path {}", path, schema, additionalSearchPath);
            try {
                var sqlStatements = SQLScriptUtils.parseSQLScript(path).stream().map(s -> s.replace("{schema}", schema)).toList();
                SQLScriptUtils.executeSQLBatches(conn, sqlStatements, batchSize);
                count.incrementAndGet();
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        };

        try {
            for (var folder : folders) {
                executeScriptsInFolder(Path.of(sqlResPath, folder), predicate, consumer);
            }
        } catch (IOException e) {
            throw new SystemException(e);
        }

        return count.intValue();
    }

    @Override
    public void deleteTenant(String tenant) {
        DBUtils.transactionCall(ds, masterSchema, conn -> {
            var sql = "delete from ecm_tenants where tenant = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                if (stmt.executeUpdate() < 1) {
                    throw new NotFoundException(String.format("Unable to delete tenant '%s' from master schema list", tenant));
                }
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void cleanTenant(String tenant, String schema) {
        log.info("Deleting all data of tenant '{}' from schema '{}'", tenant, schema);
        DBUtils.transactionCall(ds, ObjectUtils.append(schema, additionalSearchPath), conn -> {
            try {
                cleanTenantSQL(conn, "delete from ecm_removed_nodes where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_versions where node_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_versions where node_id in (select id from ecm_archived_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_external_properties where node_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_external_properties where node_id in (select id from ecm_archived_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_archived_associations where child_id in (select id from ecm_archived_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_archived_associations where parent_id in (select id from ecm_archived_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_archived_associations where child_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_archived_associations where parent_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_archived_nodes where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_paths where node_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_associations where child_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_associations where parent_id in (select id from ecm_nodes where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_nodes where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_access_rules where sg_id in (select id from ecm_security_groups where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_security_groups where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_async_operations where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_dict_entries where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_pub_keys where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_user_groups where user_id in (select id from ecm_users where tenant = ?)", tenant);
                cleanTenantSQL(conn, "delete from ecm_groups where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_users where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_models where tenant = ?", tenant);
                cleanTenantSQL(conn, "delete from ecm_archived_models where tenant = ?", tenant);

                var tenantIsolationEnabled = findByIdOptional(tenant)
                        .map(TenantSpace::getData)
                        .map(TenantData::getTenantIsolationEnabled)
                        .orElse(this.tenantIsolationEnabled);
                if (tenantIsolationEnabled) {
                    cleanTenantSQL(conn, "delete from ecm_files where tenant = ?", tenant);
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            log.info("All data from tenant '{}' has been removed", tenant);
            return null;
        });
    }

    private void cleanTenantSQL(Connection conn, String sql, String tenant) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenant);
            int n = stmt.executeUpdate();
            log.info("{} rows deleted by sql '{}'", n, sql);
        }
    }

    private void executeScriptsInFolder(Path path, Consumer<Path> consumer) throws IOException {
        executeScriptsInFolder(path, (f) -> true, consumer);
    }

    private void executeScriptsInFolder(Path path, Predicate<String> predicate, Consumer<Path> consumer) throws IOException {
        try (var stream = Files.list(path)) {
            stream
                .filter(f -> {
                    var name = f.getFileName().toString().toLowerCase();
                    return !name.startsWith(".") && !name.startsWith("_") && name.endsWith(".sql");
                })
                .filter(f -> predicate.test(f.getFileName().toString()))
                .sorted(Comparator.comparing(Path::getFileName))
                .forEach(consumer);
        }
    }

    public void initialize(boolean forced) {
        DBUtils.transactionCall(ds, ObjectUtils.append(masterSchema, additionalSearchPath), conn -> {
            try {
                if (forced) {
                    initializeMasterSchema(conn);
                }

                try (Statement stmt = conn.createStatement()) {
                    lockMasterSchema(stmt, true);

                    try (var rs = stmt.executeQuery("select count(*) from ecm_models where tenant = 'ANY'")) {
                        if (!rs.next() || (!forced && rs.getInt(1) > 0)) {
                            log.info("Already initialized");
                            return null;
                        }
                    }
                }

                updateCommonModelsIfRequired(conn);
            } catch (SQLException | IOException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    private void lockMasterSchema(Statement stmt, boolean accessExclusive) throws SQLException {
        log.info("Locking tenant table (access exclusive: {})", accessExclusive);
        var sql = accessExclusive ? "lock table ecm_tenants in exclusive mode" : "lock table ecm_tenants";
        stmt.execute(sql);
        log.debug("Locked");
    }

    private void initializeMasterSchema(Connection conn) {
        log.info("Initializing database schema '{}' using additional search path {}", masterSchema, additionalSearchPath);
        Consumer<Path> consumer = path -> {
            log.info("Processing script at {} on schema {}", path, masterSchema);
            try {
                var sqlStatements = SQLScriptUtils.parseSQLScript(path).stream().map(s -> s.replace("{schema}", masterSchema)).toList();
                SQLScriptUtils.executeSQLBatches(conn, sqlStatements, 1000);
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        };

        try {
            executeScriptsInFolder(Path.of(sqlResPath, "master"), consumer);
            executeScriptsInFolder(Path.of(sqlResPath, "models"), consumer);
            executeScriptsInFolder(Path.of(sqlResPath, "tenant"), consumer);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    public int updateCommonModelsIfRequired() {
        return DBUtils.transactionCall(ds, masterSchema, conn -> {
            try {
                return updateCommonModelsIfRequired(conn);
            } catch (SQLException | IOException e) {
                throw new SystemException(e);
            }
        });
    }

    private int updateCommonModelsIfRequired(Connection conn) throws SQLException, IOException {
        try (Statement stmt = conn.createStatement()) {
            try (var rs = stmt.executeQuery("select count(*) from ecm_models where tenant = 'ANY'")) {
                if (rs.next()) {
                    log.info("{} common models found in schema '{}'", rs.getInt(1), masterSchema);
                }
            }
        } catch (SQLException e) {
            log.error("Error while reading common models: {}", e.getMessage());
            return -1;
        }

        var selectSQL = """
                    select data from ecm_models\s
                    where tenant = ? and model_name = ?
                    """;

        var updateSQL = """
                    update ecm_models set\s
                    data = ?, fmt = ?\s
                    where tenant = ? and model_name = ?
                    """;

        var insertSQL = """
                    insert into ecm_models (tenant,model_name,fmt,is_active,data) values (?,?,?,?,?)\s
                    on conflict (tenant,model_name) do update set\s
                    fmt = excluded.fmt, data = excluded.data
                    """;

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSQL);
             PreparedStatement updateStmt = conn.prepareStatement(updateSQL);
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
            selectStmt.setString(1,"ANY");
            updateStmt.setString(3,"ANY");
            insertStmt.setString(1,"ANY");
            insertStmt.setBoolean(4, true);

            var count = new AtomicInteger(0);
            try (var list = Files.list(Paths.get("res/models"))) {
                list.sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(p -> {
                    var filename = p.getFileName().toString();
                    if (!Strings.CS.startsWith(filename,".")) {
                        var elems = filename.split("#");
                        filename = elems[elems.length - 1];
                        var dot = filename.lastIndexOf('.');
                        if (dot >= 0) {
                            var ext = filename.substring(dot + 1);
                            var name = filename.substring(0, dot).replace("_", ":");

                            try (var is = Files.newInputStream(p)) {
                                var buffer = IOUtils.readFully(is);
                                selectStmt.setString(2, name);
                                try (ResultSet rs = selectStmt.executeQuery()) {
                                    if (rs.next()) {
                                        var data = rs.getString(1);
                                        var currentHash = ObjectUtils.hash(data, "MD5");
                                        var expectedHash = ObjectUtils.hash(buffer, "MD5");
                                        if (!currentHash.equals(expectedHash)) {
                                            log.info("Updating model '{}': current hash '{}', expected hash '{}'", name, currentHash, expectedHash);
                                            updateStmt.setString(1, new String(buffer, StandardCharsets.UTF_8));
                                            updateStmt.setString(2, ext.toLowerCase());
                                            updateStmt.setString(4, name);
                                            if (updateStmt.executeUpdate() > 0) {
                                                log.info("Model '{}' updated", name);
                                                count.incrementAndGet();
                                            }
                                        } else {
                                            log.info("Model '{}' is up to date", name);
                                        }
                                    } else {
                                        log.info("Creating model '{}'", name);
                                        insertStmt.setString(2, name);
                                        insertStmt.setString(3, ext.toLowerCase());
                                        insertStmt.setString(5, new String(buffer, StandardCharsets.UTF_8));
                                        if (insertStmt.executeUpdate() > 0) {
                                            log.info("Model '{}' created", name);
                                            count.incrementAndGet();
                                        }
                                    }
                                } // end try rs
                            } catch (NoSuchAlgorithmException | IOException | SQLException e) {
                                log.error("Unable to update model from file '{}': {}", filename, e.getMessage());
                                throw new SystemException(e);
                            }
                        } // end if dot
                    }
                });
            }

            var n = count.intValue();
            log.info("{} common models updated", n);
            return n;
        }
    }

    @Override
    public void persist(TenantSpace t) {
        DBUtils.call(ds, masterSchema, conn -> {
            try {
                final var sql = """
                    insert into ecm_tenants (tenant,root_id,schema_name,data) values (?,?,?,?::jsonb)\s
                    on conflict (tenant) do update set\s
                    root_id = excluded.root_id, schema_name = excluded.schema_name,\s
                    data = coalesce(ecm_tenants.data, '{}'::jsonb) || excluded.data::jsonb
                    """;
                try (PreparedStatement stmt  = conn.prepareStatement(sql)) {
                    stmt.setString(1, t.getTenant());
                    stmt.setObject(2, t.getRootId() == null ? null : t.getRootId());
                    stmt.setString(3, t.getSchema());
                    stmt.setString(4, Optional.ofNullable(t.getData()).map(data -> {
                        try {
                            return objectMapper.writeValueAsString(t.getData());
                        } catch (JsonProcessingException e) {
                            throw new SystemException(e);
                        }
                    }).orElse(null));
                    return stmt.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<TenantSpace> findAll() {
        return findStartingWith(null);
    }

    @Override
    public List<TenantSpace> findStartingWith(String prefix) {
        return DBUtils.call(ds, masterSchema, conn -> {
            try {
                var sql = "select tenant,root_id,schema_name,data from ecm_tenants";
                if (StringUtils.isNotBlank(prefix)) {
                    sql += " where lower(tenant) like ?";
                }
                sql += " order by tenant";

                try (var stmt = conn.prepareStatement(sql)) {
                    if (StringUtils.isNotBlank(prefix)) {
                        stmt.setString(1, StringUtils.stripToEmpty(prefix).toLowerCase() + "%");
                    }

                    try (var rs = stmt.executeQuery()) {
                        return list(rs);
                    }
                }
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void lockTenantTable() {
        DBUtils.call(ds, masterSchema, conn -> {
            try (Statement stmt  = conn.createStatement()) {
                lockMasterSchema(stmt, false);
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public Optional<TenantSpace> findByIdOptional(String name) {
        final var tenantName = StringUtils.stripToEmpty(name).toLowerCase();
        TenantSpace t = tenantCache.getIfPresent(tenantName);
        if (t != null) {
            return Optional.of(t);
        }

        return DBUtils.call(ds, masterSchema, conn -> {
            try {
                final var sql = """
                    select t.tenant,t.root_id,t.schema_name,t.data\s
                    from ecm_tenants t\s
                    where lower(t.tenant) = ?
                    """;
                try (PreparedStatement stmt  = conn.prepareStatement(sql)) {
                    stmt.setString(1, tenantName);
                    try (var rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }

                        var tenantSpace = read(rs);
                        if (tenantSpace.isValid()) {
                            tenantCache.put(tenantName.toLowerCase(), tenantSpace);
                        }
                        log.trace("Tenant '{}' loaded into cache: {}", tenantName, tenantSpace.getData());

                        return Optional.of(tenantSpace);
                    }
                }
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }

    private List<TenantSpace> list(ResultSet rs) throws SQLException, JsonProcessingException {
        final List<TenantSpace> tenants = new LinkedList<>();
        while (rs.next()) {
            tenants.add(read(rs));
        }
        return tenants;
    }

    private TenantSpace read(ResultSet rs) throws SQLException, JsonProcessingException {
        TenantSpace t = new TenantSpace();
        t.setTenant(rs.getString("tenant"));
        t.setRootId(DBUtils.getLong(rs, "root_id"));
        t.setSchema(rs.getString("schema_name"));

        var s = rs.getString("data");
        if (s != null) {
            t.setData(objectMapper.readValue(s, TenantData.class));
        } else {
            t.setData(new TenantData());
        }

        return t;
    }
}
