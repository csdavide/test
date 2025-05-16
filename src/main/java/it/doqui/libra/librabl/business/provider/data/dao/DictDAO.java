package it.doqui.libra.librabl.business.provider.data.dao;

import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class DictDAO extends AbstractDAO {

    public Optional<String> getPayload(String ns, String key) {
        return call(conn -> {
            final var sql = """
                select payload from ecm_dict_entries\s
                where tenant = ? and ns = ? and k = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setString(2, ns);
                stmt.setString(3, key);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString("payload")) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }
}
