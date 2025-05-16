package it.doqui.libra.librabl.business.provider.data.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.data.entities.User;
import it.doqui.libra.librabl.business.provider.data.entities.UserData;
import it.doqui.libra.librabl.business.provider.data.entities.UserGroup;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.ConflictException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class UserDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public Paged<User>findUsers(String usernamePrefix, Long groupId, Pageable pageable) {
        return call(conn -> {
            final var fields = "u.id,u.tenant,u.uuid,u.username,u.data";
            final var order = "u.username";
            var sql = """
            select {fields}\s
            from ecm_users u\s
            where u.tenant = ?
            """;

            if (StringUtils.isNotBlank(usernamePrefix)) {
                sql += " and lower(u.username) "
                    + (usernamePrefix.contains("%") ? "like" : "=")
                    + " ?";
            }

            if (groupId != null) {
                sql += " and u.id in (select user_id from ecm_user_groups where group_id = ?)";
            }

            try {
                return find(conn, sql, fields, order, pageable,
                    stmt -> {
                        try {
                            var c = 0;
                            stmt.setString(++c, UserContextManager.getTenant());
                            if (StringUtils.isNotBlank(usernamePrefix)) {
                                stmt.setString(++c, usernamePrefix.toLowerCase());
                            }
                            if (groupId != null) {
                                stmt.setLong(++c, groupId);
                            }
                            return c;
                        } catch (SQLException e) {
                            throw new SystemException(e);
                        }
                    },
                    rs -> {
                        try {
                            var user = new User();
                            fillUser(rs, user);
                            return user;
                        } catch (SQLException | JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<User>findUser(String name) {
        return findUser(name, false);
    }

    public Optional<User>findUser(String name, boolean includeGroups) {
        return call(conn -> {

            try {
                final var sql = """
                    select id,tenant,uuid,username,data\s
                    from ecm_users\s
                    where tenant = ? and lower(username) = ?
                    """;
                var user = new User();
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, UserContextManager.getTenant());
                    stmt.setString(2, StringUtils.lowerCase(name));
                    try (var rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }

                        fillUser(rs, user);
                    }
                }

                if (includeGroups) {
                    final var  groupSQL = """
                        select g.id,g.tenant,g.groupname\s
                        from ecm_groups g\s
                        join ecm_user_groups u on g.id = u.group_id\s
                        where u.user_id = ?
                        """;
                    try (var stmt = conn.prepareStatement(groupSQL)) {
                        stmt.setLong(1, user.getId());
                        try (var rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                var group = new UserGroup();
                                fillGroup(rs, group);
                                user.getGroups().add(group);
                            }
                        }
                    }

                    var group = new UserGroup();
                    group.setGroupname("GROUP_EVERYONE");
                    group.setTenant(user.getTenant());
                    user.getGroups().add(group);
                }

                return Optional.of(user);
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }

    public void createUser(User user, boolean ignoreIfExist) {
        call(conn -> {
           var sql = """
               insert into ecm_users (tenant,uuid,username,data)\s
               values (?,?,?,?::jsonb)\s
               """;

           if (ignoreIfExist) {
               sql += """
                   on conflict (tenant,lower(username)) do update set\s
                   data = ecm_users.data || coalesce(excluded.data,'{}'::jsonb)\s
                   """;
           }

           sql += "returning id,tenant,uuid,username,data";

           try (var stmt = conn.prepareStatement(sql)) {
               stmt.setString(1, user.getTenant());
               stmt.setString(2, user.getUuid());
               stmt.setString(3, user.getUsername());
               stmt.setString(4, user.getData() == null ? null : objectMapper.writeValueAsString(user.getData()));
               stmt.execute();
               try (var rs = stmt.getResultSet()) {
                   if (rs.next()) {
                       fillUser(rs, user);
                   }
               }
           } catch (SQLException | JsonProcessingException e) {
               log.error(e.getMessage(), e);
               throw new SystemException(e);
           }

           return null;
        });
    }

    public void updateUser(User user) {
        call(conn -> {
            try (var stmt = conn.prepareStatement("update ecm_users set data = ?::jsonb where id = ?")) {
                stmt.setString(1, user.getData() == null ? null : objectMapper.writeValueAsString(user.getData()));
                stmt.setLong(2, user.getId());
                stmt.executeUpdate();
            } catch (SQLException | JsonProcessingException e) {
                log.error(e.getMessage(), e);
                throw new SystemException(e);
            }

            return null;
        });
    }

    public void deleteUser(long userId) {
        call(conn -> {
            try {
                try (var stmt = conn.prepareStatement("delete from ecm_user_groups where user_id = ?")) {
                    stmt.setLong(1, userId);
                    stmt.executeUpdate();
                }

                try (var stmt = conn.prepareStatement("delete from ecm_users where id = ?")) {
                    stmt.setLong(1, userId);
                    stmt.executeUpdate();
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Paged<UserGroup>findGroups(String groupPrefix, Pageable pageable) {
        return call(conn -> {
            final var fields = "g.id,g.tenant,g.groupname";
            final var order = "g.groupname";
            var sql = """
            select {fields}\s
            from ecm_groups g\s
            where g.tenant = ?
            """;

            if (StringUtils.isNotBlank(groupPrefix)) {
                sql += " and g.groupname "
                    + (groupPrefix.contains("%") ? "like" : "=")
                    + " ?";
            }

            try {
                return find(conn, sql, fields, order, pageable,
                    stmt -> {
                        try {
                            var c = 0;
                            stmt.setString(++c, UserContextManager.getTenant());
                            if (StringUtils.isNotBlank(groupPrefix)) {
                                stmt.setString(++c, groupPrefix);
                            }
                            return c;
                        } catch (SQLException e) {
                            throw new SystemException(e);
                        }
                    },
                    rs -> {
                        try {
                            var group = new UserGroup();
                            fillGroup(rs, group);
                            return group;
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<UserGroup>findGroup(String name) {
        return call(conn -> {
            final var sql = """
                select id,tenant,groupname\s
                from ecm_groups\s
                where tenant = ? and groupname = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setString(2, name);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var group = new UserGroup();
                        fillGroup(rs, group);
                        return Optional.of(group);
                    }

                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void createGroup(UserGroup group) {
        call(conn -> {
            var sql = "insert into ecm_groups (tenant,groupname) values (?,?)";
            try (var stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, group.getTenant());
                stmt.setString(2, group.getGroupname());

                if (stmt.executeUpdate() < 1) {
                    throw new RuntimeException("Unable to create group");
                }

                try (var generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        group.setId(generatedKeys.getLong(1));
                    } else {
                        throw new RuntimeException("Group creation failed, no ID obtained.");
                    }
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void deleteGroup(long groupId) {
        call(conn -> {
            try {
                try (var stmt = conn.prepareStatement("delete from ecm_user_groups where group_id = ?")) {
                    stmt.setLong(1, groupId);
                    stmt.executeUpdate();
                }

                try (var stmt = conn.prepareStatement("delete from ecm_groups where id = ?")) {
                    stmt.setLong(1, groupId);
                    stmt.executeUpdate();
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void addUserToGroup(long userId, long groupId) {
        call(conn -> {
            var sql = "insert into ecm_user_groups (user_id,group_id) values (?,?) on conflict (user_id, group_id) do nothing";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                int n = stmt.executeUpdate();
                if (n < 1) {
                    throw new ConflictException("User already added in the requested group");
                }
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public boolean removeUserFromGroup(long userId, long groupId) {
        return call(conn -> {
            var sql = "delete from ecm_user_groups where user_id = ? and group_id = ?";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void fillUser(ResultSet rs, User user) throws SQLException, JsonProcessingException {
        user.setId(rs.getLong("id"));
        user.setTenant(rs.getString("tenant"));
        user.setUuid(rs.getString("uuid"));
        user.setUsername(rs.getString("username"));

        var data = rs.getString("data");
        if (data != null) {
            user.getData().copy(objectMapper.readValue(data, UserData.class));
        }
    }

    private void fillGroup(ResultSet rs, UserGroup group) throws SQLException {
        group.setId(rs.getLong("id"));
        group.setTenant(rs.getString("tenant"));
        group.setGroupname(rs.getString("groupname"));
    }
}
