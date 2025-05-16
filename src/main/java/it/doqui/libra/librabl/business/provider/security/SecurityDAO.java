package it.doqui.libra.librabl.business.provider.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.business.provider.data.entities.User;
import it.doqui.libra.librabl.business.provider.data.entities.UserData;
import it.doqui.libra.librabl.business.provider.data.entities.UserGroup;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.security.PkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class SecurityDAO {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @Inject
    ObjectMapper objectMapper;

    @SuppressWarnings("ALL")
    public Optional<User> findUser(@NotNull AuthorityRef authorityRef, @NotNull String schema) {
        return DBUtils.call(ds, schema, conn -> {
            var sql = "select id,tenant,uuid,username,data from ecm_users where tenant = ? and lower(username) = ?";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, authorityRef.getTenantRef().toString());
                stmt.setString(2, authorityRef.getIdentity().toLowerCase());
                try (var rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    var user = readUser(rs);
                    fillGroups(conn, user);
                    return Optional.of(user);
                }
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    Collection<PkItem> listPublicKeys(@NotNull TenantRef tenantRef, @NotNull String schema) {
        return DBUtils.call(ds, schema, conn -> {
            var sql = """
                select k.kid,k.pub_key,k.username,k.scopes\s
                from ecm_pub_keys k\s
                where k.tenant = ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantRef.toString());
                try (var rs = stmt.executeQuery()) {
                    var items = new ArrayList<PkItem>();
                    while (rs.next()) {
                        var pk = new PkItem();
                        pk.setKid(rs.getString("kid"));
                        pk.setKey(rs.getString("pub_key"));
                        pk.setUsername(rs.getString("username"));

                        var scopes = rs.getString("scopes");
                        if (StringUtils.isNotBlank(scopes)) {
                            var scopeArray = scopes.split(" ");
                            for (var s : scopeArray) {
                                var value = StringUtils.strip(s);
                                if (StringUtils.isNotBlank(value)) {
                                    pk.getScopes().add(value);
                                }
                            }
                        }

                        items.add(pk);
                    }

                    return items;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    void persistPublicKey(@NotNull TenantRef tenantRef, @NotNull String schema, PkItem item) {
        DBUtils.call(ds, schema, conn -> {
            var sql = """
                insert into ecm_pub_keys (tenant,kid,pub_key,username,scopes)\s
                values (?,?,?,?,?)\s
                on conflict (tenant,kid) do update set\s
                pub_key = excluded.pub_key, username = excluded.username, scopes = excluded.scopes
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantRef.toString());
                stmt.setString(2, item.getKid());
                stmt.setString(3, item.getKey());
                stmt.setString(4, item.getUsername());
                stmt.setString(5, String.join(" ", item.getScopes()));
                stmt.executeUpdate();
            } catch (SQLException e) {
                if (StringUtils.contains(e.getMessage(), "duplicate key")) {
                    throw new ConflictException("Public Key already present");
                }

                throw new RuntimeException(e);
            }

            return null;
        });
    }

    boolean deletePublicKey(@NotNull TenantRef tenantRef, @NotNull String schema, String kid) {
        return DBUtils.call(ds, schema, conn -> {
            var sql = """
            delete from ecm_pub_keys where tenant = ?\s
            and (kid = ? or pub_key = ?)
            """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantRef.toString());
                stmt.setString(2, kid);
                stmt.setString(3, kid);
                return stmt.executeUpdate() != 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    Optional<AuthenticatingUserRepresentation> findUserByKid(String kid, @NotNull AuthorityRef authorityRef, @NotNull String schema) {
        return DBUtils.call(ds, schema, conn -> {
            var sql = """
            select u.id,u.tenant,u.uuid,u.username,u.data,k.pub_key,k.scopes\s
            from ecm_users u\s
            join ecm_pub_keys k on (u.tenant = k.tenant and (u.username = k.username or k.username = 'admin'))\s
            where k.tenant = ? and k.kid = ? and lower(u.username) = ?
            """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, authorityRef.getTenantRef().toString());
                stmt.setString(2, kid);
                stmt.setString(3, StringUtils.lowerCase(authorityRef.getIdentity()));
                return findAuthenticatedUser(stmt);
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    Optional<AuthenticatingUserRepresentation> findUserByPubKey(String pubKey, @NotNull String tenant, @NotNull String schema) {
        return DBUtils.call(ds, schema, conn -> {
            var sql = """
            select u.id,u.tenant,u.uuid,u.username,u.data,k.pub_key,k.scopes\s
            from ecm_users u join ecm_pub_keys k on (u.tenant = k.tenant and u.username = k.username)\s
            where k.tenant = ? and k.pub_key = ?
            """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                stmt.setString(2, pubKey);
                return findAuthenticatedUser(stmt);
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Optional<AuthenticatingUserRepresentation> findAuthenticatedUser(PreparedStatement stmt) throws SQLException, JsonProcessingException {
        try (var rs = stmt.executeQuery()) {
            if (rs.next()) {
                var user = readUser(rs);
                fillGroups(stmt.getConnection(), user);
                var scopes = rs.getString("scopes");
                var pk = rs.getString("pub_key");

                var au = new AuthenticatingUserRepresentation();
                au.setUser(user);

                if (StringUtils.isNotBlank(pk)) {
                    au.setPublicKey(pk);
                }

                if (StringUtils.isNotBlank(scopes)) {
                    var scopeArray = scopes.split(" ");
                    for (var s : scopeArray) {
                        var value = StringUtils.strip(s);
                        if (StringUtils.isNotBlank(value)) {
                            au.getScopes().add(value);
                        }
                    }
                }

                return Optional.of(au);
            }

            return Optional.empty();
        }
    }

    private User readUser(ResultSet rs) throws SQLException, JsonProcessingException {
        var user = new User();
        user.setId(rs.getLong("id"));
        user.setTenant(rs.getString("tenant"));
        user.setUuid(rs.getString("uuid"));
        user.setUsername(rs.getString("username"));
        user.getData().copy(objectMapper.readValue(Objects.requireNonNullElse(rs.getString("data"), "{}"), UserData.class));
        return user;
    }

    private void fillGroups(Connection conn, User user) throws SQLException {
        var sql = """
            select g.id, g.groupname\s
            from ecm_groups g\s
            join ecm_user_groups u on u.group_id  = g.id\s
            and u.user_id = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, user.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var g = new UserGroup();
                    g.setId(rs.getLong("id"));
                    g.setGroupname(rs.getString("groupname"));
                    user.getGroups().add(g);
                }
            }
        }
    }

}
