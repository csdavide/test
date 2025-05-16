package it.doqui.libra.librabl.business.provider.mimetypes;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.CleanCacheEvent;
import it.doqui.libra.librabl.business.service.interfaces.ManagementService;
import it.doqui.libra.librabl.business.service.interfaces.MimeTypeService;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.management.MgmtOperation;
import it.doqui.libra.librabl.views.mimetype.MimeTypeItem;
import it.doqui.libra.librabl.views.mimetype.MimeTypeRequest;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.messaging.events.EventType.CLEAN_CACHE;

@ApplicationScoped
@Slf4j
public class MimeTypeManager implements MimeTypeService {

    private final AtomicReference<MimetypeCache> mimetypeCache = new AtomicReference<>();

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @Inject
    ManagementService managementService;

    @PostConstruct
    void init() {
        onStart(new CleanCacheEvent());
    }

    synchronized void onStart(@Observes CleanCacheEvent ev) {
        var m = new MimetypeCache();
        m.load();
        this.mimetypeCache.set(m);
        log.info("Mimetype Cache updated with {} entries", m.size);
    }

    @Override
    public Optional<MimeTypeItem> getById(long id) {
        return DBUtils.call(ds, masterSchema, conn -> {
            try (var stmt = conn.prepareStatement("select id,file_extension,mimetype,priority from ecm_mimetypes where id = ?")) {
                stmt.setLong(1, id);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void deleteById(long id) {
        DBUtils.transactionCall(ds, masterSchema, conn -> {
            try (var stmt = conn.prepareStatement("delete from ecm_mimetypes where id = ?")) {
                stmt.setLong(1, id);
                if (stmt.executeUpdate() < 1) {
                    throw new NotFoundException("" + id);
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });

        postReload();
    }

    @Override
    public void delete(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }

        DBUtils.transactionCall(ds, masterSchema, conn -> {
            try (var stmt = conn.prepareStatement("delete from ecm_mimetypes where id = any(?)")) {
                stmt.setArray(1, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });

        postReload();
    }

    @Override
    public void addAll(Collection<MimeTypeRequest> items) {
        DBUtils.transactionCall(ds, masterSchema, conn -> {
            try {
                addAll(conn, items);
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });

        postReload();
    }

    @Override
    public void replaceAll(Collection<MimeTypeRequest> items) {
        DBUtils.transactionCall(ds, masterSchema, conn -> {
            try {
                removeAll(conn);
                addAll(conn, items);
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });

        postReload();
    }

    private void postReload() {
        var operation = new MgmtOperation();
        operation.setOp(MgmtOperation.MgmtOperationType.SENDEVENT);
        var operand = new MgmtOperation.SendEventOperand();
        operand.setEvent(CLEAN_CACHE);
        operation.setOperand(operand);
        managementService.performOperations(List.of(operation));
    }

    private void addAll(Connection conn, Collection<MimeTypeRequest> items) throws SQLException {
        if (items.isEmpty()) {
            return;
        }

        var sql = """
            insert into ecm_mimetypes (mimetype, file_extension, priority)\s
            values (?,?,?) on conflict (mimetype, file_extension) do nothing
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            for (var mt : items) {
                if (StringUtils.isBlank(mt.getMimetype()) || StringUtils.isBlank(mt.getFileExtension())) {
                    throw new BadRequestException();
                }

                stmt.setString(1, mt.getMimetype());
                stmt.setString(2, mt.getFileExtension());
                stmt.setInt(3, mt.getPriority());
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    private void removeAll(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("delete from ecm_mimetypes");
        }
    }

    @Override
    public Optional<String> getFileExtension(String mimeType, boolean includeStarExtensions) {
        var criteria = new MimeTypeItem();
        criteria.setMimetype(mimeType);
        var result = list(criteria, includeStarExtensions);
        return result
            .stream()
            .sorted(Comparator.comparingInt(MimeTypeRequest::getPriority))
            .map(MimeTypeItem::getFileExtension)
            .findFirst();
    }

    @Override
    public List<String> getAllFileExtensions(String mimeType, boolean includeStarExtensions) {
        var criteria = new MimeTypeItem();
        criteria.setMimetype(mimeType);
        var result = list(criteria, includeStarExtensions);
        return result
            .stream()
            .sorted(Comparator.comparingInt(MimeTypeRequest::getPriority))
            .map(MimeTypeItem::getFileExtension)
            .toList();
    }

    @Override
    public Set<String> getAllMimeTypes(String fileExtension) {
        var criteria = new MimeTypeItem();
        criteria.setFileExtension(fileExtension);
        var result = list(criteria, true);
        return result
            .stream()
            .sorted(Comparator.comparingInt(MimeTypeRequest::getPriority))
            .map(MimeTypeItem::getMimetype)
            .collect(Collectors.toSet());
    }

    @Override
    public List<MimeTypeItem> list(MimeTypeItem criteria, boolean includeStarExtensions) {
        var m = mimetypeCache.get();
        List<MimeTypeItem> result = new ArrayList<>();
        Multimap<String, MimeTypeItem> map = null;
        String key = null;
        var appendStarExt = false;
        if (!StringUtils.isBlank(criteria.getFileExtension())) {
            map = m.mapByExtension;
            var parts = criteria.getFileExtension().split("\\.");
            key = parts[parts.length - 1];
        } else if (!StringUtils.isBlank(criteria.getMimetype())) {
            map = m.mapByMimetype;
            key = criteria.getMimetype();
            appendStarExt = true;
        }

        if (map != null) {
            result.addAll(map.get(key.toLowerCase()));
        } else {
            result.addAll(m.mapByMimetype.values());
        }

        if (includeStarExtensions && appendStarExt) {
            m.starExtSet.forEach(ext -> {
                var item = new MimeTypeItem();
                item.setMimetype(criteria.getMimetype());
                item.setFileExtension(ext);
                result.add(item);
            });
        }

        return result;
    }

    private MimeTypeItem read(ResultSet rs) throws SQLException {
        var x = new MimeTypeItem();
        x.setId(rs.getLong("id"));
        x.setFileExtension(rs.getString("file_extension"));
        x.setMimetype(rs.getString("mimetype"));
        x.setPriority(rs.getInt("priority"));
        return x;
    }

    private class MimetypeCache {
        private final Multimap<String, MimeTypeItem> mapByExtension;
        private final Multimap<String, MimeTypeItem> mapByMimetype;
        private final Set<String> starExtSet;
        private long size;

        public MimetypeCache() {
            mapByExtension = ArrayListMultimap.create();
            mapByMimetype = ArrayListMultimap.create();
            starExtSet = new HashSet<>();
            size = 0;
        }

        public void load() {
            retrieveAll(m -> {
                mapByExtension.put(m.getFileExtension(), m);
                if (StringUtils.equals(m.getMimetype(), "*")) {
                    starExtSet.add(m.getFileExtension());
                } else {
                    mapByMimetype.put(m.getMimetype(), m);
                }

                size++;
            });
        }

        private void retrieveAll(Consumer<MimeTypeItem> f) {
            DBUtils.call(ds, masterSchema, conn -> {
                try (var stmt = conn.createStatement()) {
                    final String sql = """
                        select id,file_extension,mimetype,priority\s
                        from ecm_mimetypes\s
                        order by priority,mimetype
                        """;
                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            f.accept(read(rs));
                        }
                    }

                    return null;
                } catch (SQLException e) {
                    throw new SystemException(e);
                }
            });
        }
    }
}
