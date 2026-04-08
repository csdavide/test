package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.ConfigurationRepository;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

@Slf4j
public abstract class AbstractDAO {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    protected AgroalDataSource ds;

    @Inject
    protected SessionContext sessionContext;

    @Inject
    protected ConfigurationRepository configurationRepository;

    protected <R> R call(Function<Connection,R> f) {
        return DBUtils.call(ds, sessionContext.getUserContext().getDbSchema(), f);
    }

    protected <R> R call(String schema, Function<Connection,R> f) {
        return DBUtils.call(ds, Optional.ofNullable(schema).orElseGet(() -> sessionContext.getUserContext().getDbSchema()), f);
    }

    private String formatSQL(final String sql, final String fields, final String order, Pageable pageable, boolean counting) {
        var result = sql;
        if (!counting) {
            result += " order by " + order;

            if (pageable != null) {
                result += " offset ? limit ?";
            }
        }

        return result.replace("{fields}", counting ? "count(*)" : fields);
    }

    protected <T> Paged<T> find(Connection conn, final String sql, final String fields, final String order, Pageable pageable, ToIntFunction<PreparedStatement> setParam, Function<ResultSet,T> reader) throws SQLException {
        var totalElements = 0;
        if (pageable != null) {
            //noinspection SqlSourceToSinkFlow
            try (var stmt = conn.prepareStatement(formatSQL(sql, fields, order, pageable, true))) {
                setParam.applyAsInt(stmt);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalElements = rs.getInt(1);
                    }
                }
            }
        }

        var items = new LinkedList<T>();
        //noinspection SqlSourceToSinkFlow
        try (var stmt = conn.prepareStatement(formatSQL(sql, fields, order, pageable, false))) {
            var c = setParam.applyAsInt(stmt);
            if (pageable != null) {
                stmt.setInt(++c, pageable.getPage() * pageable.getSize());
                stmt.setInt(++c, pageable.getSize());
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var item = reader.apply(rs);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }

        if (pageable == null) {
            return new Paged<>(items);
        } else {
            return new Paged<>(pageable.getPage(), pageable.getSize(), totalElements, items);
        }
    }
}
