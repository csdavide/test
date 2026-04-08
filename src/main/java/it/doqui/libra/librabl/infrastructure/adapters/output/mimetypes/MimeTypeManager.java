package it.doqui.libra.librabl.infrastructure.adapters.output.mimetypes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.application.model.events.SendEventRequest;
import it.doqui.libra.librabl.application.ports.out.EventRepository;
import it.doqui.libra.librabl.domain.model.mimetypes.MimeTypeMapping;
import it.doqui.libra.librabl.domain.ports.out.MimeTypeRepository;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.platform.events.MimeTypeReloadEvent;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static it.doqui.libra.librabl.application.model.events.EventType.RELOAD_MIMETYPES;

@ApplicationScoped
@Slf4j
public class MimeTypeManager implements MimeTypeRepository {

    private final AtomicReference<MimetypeCache> mimetypeCache = new AtomicReference<>();

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @ConfigProperty(name = "libra.mimetypes.service-url")
    Optional<String> remoteMimetypeServiceUrl;

    @Inject
    EventRepository eventRepository;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    void init() {
        onStart(new MimeTypeReloadEvent());
    }

    synchronized void onStart(@Observes MimeTypeReloadEvent ev) {
        var m = new MimetypeCache();
        m.load();
        this.mimetypeCache.set(m);
        log.info("Mimetype Cache updated with {} entries", m.size);
    }

    @Override
    public Optional<MimeTypeMapping> getById(long id) {
        var mt = mimetypeCache.get().mapById.get(id);
        if (mt != null) {
            return Optional.of(mt);
        } else if (remoteMimetypeServiceUrl.isPresent()) {
            return Optional.empty();
        }

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
        remoteMimetypeServiceUrl.ifPresent(url -> {
            throw new ForbiddenException("Mimetype update is not allowed");
        });

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
        remoteMimetypeServiceUrl.ifPresent(url -> {
            throw new ForbiddenException("Mimetype update is not allowed");
        });

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
    public void addAll(Collection<MimeTypeMapping> items) {
        remoteMimetypeServiceUrl.ifPresent(url -> {
            throw new ForbiddenException("Mimetype update is not allowed");
        });

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
    public void replaceAll(Collection<MimeTypeMapping> items) {
        remoteMimetypeServiceUrl.ifPresent(url -> {
            throw new ForbiddenException("Mimetype update is not allowed");
        });

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
        eventRepository.sendEvent(new SendEventRequest(RELOAD_MIMETYPES));
    }

    private void addAll(Connection conn, Collection<MimeTypeMapping> items) throws SQLException {
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
        var criteria = new MimeTypeMapping();
        criteria.setMimetype(mimeType);
        var result = list(criteria, includeStarExtensions);
        return result
            .stream()
            .sorted(Comparator.comparingInt(MimeTypeMapping::getPriority))
            .map(MimeTypeMapping::getFileExtension)
            .findFirst();
    }

    @Override
    public List<String> getAllFileExtensions(String mimeType, boolean includeStarExtensions) {
        var criteria = new MimeTypeMapping();
        criteria.setMimetype(mimeType);
        var result = list(criteria, includeStarExtensions);
        return result
            .stream()
            .sorted(Comparator.comparingInt(MimeTypeMapping::getPriority))
            .map(MimeTypeMapping::getFileExtension)
            .toList();
    }

    @Override
    public List<String> getAllMimeTypes(String fileExtension) {
        var criteria = new MimeTypeMapping();
        criteria.setFileExtension(fileExtension);
        var result = list(criteria, true);
        return result
            .stream()
            .sorted(Comparator.comparingInt(MimeTypeMapping::getPriority))
            .map(MimeTypeMapping::getMimetype)
            .toList();
    }

    @Override
    public List<MimeTypeMapping> list(MimeTypeMapping criteria, boolean includeStarExtensions) {
        var m = mimetypeCache.get();
        List<MimeTypeMapping> result = new ArrayList<>();
        Multimap<String, MimeTypeMapping> map = null;
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
            result.addAll(map.get(key.toLowerCase()).stream().filter(Objects::nonNull).filter(mt -> includeStarExtensions || !Strings.CS.equals(mt.getMimetype(), "*")).toList());
        } else {
            result.addAll(m.mapByExtension.values().stream().filter(Objects::nonNull).filter(mt -> includeStarExtensions || !Strings.CS.equals(mt.getMimetype(), "*")).toList());
        }

        if (includeStarExtensions && appendStarExt) {
            m.starExtSet.forEach(ext -> {
                var item = new MimeTypeMapping();
                item.setMimetype(criteria.getMimetype());
                item.setFileExtension(ext);
                result.add(item);
            });
        }

        return result;
    }

    private MimeTypeMapping read(ResultSet rs) throws SQLException {
        var x = new MimeTypeMapping();
        x.setId(rs.getLong("id"));
        x.setFileExtension(rs.getString("file_extension"));
        x.setMimetype(rs.getString("mimetype"));
        x.setPriority(rs.getInt("priority"));
        return x;
    }

    private class MimetypeCache {
        private final Multimap<String, MimeTypeMapping> mapByExtension;
        private final Multimap<String, MimeTypeMapping> mapByMimetype;
        private final Set<String> starExtSet;
        private final Map<Long, MimeTypeMapping> mapById;
        private long size;

        public MimetypeCache() {
            mapByExtension = ArrayListMultimap.create();
            mapByMimetype = ArrayListMultimap.create();
            starExtSet = new HashSet<>();
            mapById = new HashMap<>();
            size = 0;
        }

        public void load() {
            Consumer<MimeTypeMapping> f = (m) -> {
                mapByExtension.put(m.getFileExtension(), m);
                if (Strings.CS.equals(m.getMimetype(), "*")) {
                    starExtSet.add(m.getFileExtension());
                } else {
                    mapByMimetype.put(m.getMimetype(), m);
                }

                if (m.getId() != null) {
                    mapById.put(m.getId(), m);
                }

                size++;
            };

            remoteMimetypeServiceUrl
                .ifPresentOrElse(url -> retrieveAllRemotely(url, f), () -> retrieveAllLocally(f));
        }

        private void retrieveAllRemotely(String url, Consumer<MimeTypeMapping> f) {
            try {
                var httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(url + (url.endsWith("/") ? "" : "/") + "api/v2/mimetypes?includeStarExtensions=true"))
                    .headers(
                        "User-Agent", "Libra/1.0.0",
                        "Accept", "application/json"
                    )
                    .GET()
                    .build();

                var httpClient = HttpClient.newBuilder().build();
                var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (httpResponse.statusCode() != 200) {
                    throw new WebException(httpResponse.statusCode(), "Unable to retrieve mimetypes from remote service");
                }

                TypeReference<ArrayList<MimeTypeMapping>> typeRef = new TypeReference<>() {};
                var mimetypes = objectMapper.readValue(httpResponse.body(), typeRef);
                for (var mt : mimetypes) {
                    f.accept(mt);
                }
            } catch (URISyntaxException | IOException e) {
                throw new SystemException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SystemException(e.getMessage());
            }
        }

        private void retrieveAllLocally(Consumer<MimeTypeMapping> f) {
            DBUtils.call(ds, masterSchema, conn -> {
                final String sql = """
                        select id,lower(file_extension) file_extension,lower(mimetype) mimetype,priority\s
                        from ecm_mimetypes\s
                        order by priority,mimetype
                        """;
                try (Statement stmt = conn.createStatement()) {
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
