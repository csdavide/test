package it.doqui.libra.librabl.business.provider.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.data.dao.AbstractDAO;
import it.doqui.libra.librabl.business.provider.data.entities.NodeData;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ContentStoreService;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;

@ApplicationScoped
@Slf4j
@Unremovable
public class RemovedNodeCleanerJob extends AbstractDAO implements MessageHandler {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ContentStoreService contentStoreService;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var uuids = ObjectUtils.getAsStrings(message.getStringProperty("uuids"));
        log.debug("Cleaning nodes {} on tenant {}", uuids, UserContextManager.getTenant());
        var items = DBUtils.transactionCall(ds, UserContextManager.getContext().getDbSchema(), conn -> {
            var nodeSql = """
                select id,uuid,wipeable,data\s
                from ecm_removed_nodes\s
                where tenant = ? and uuid = any(?)
                """;
            var decrementSql = """
                update ecm_files set counter = greatest(counter - 1,0)\s
                where tenant = ? and contentref = ? and counter >= 0\s
                returning counter
                """;
            try (PreparedStatement nodeStmt = conn.prepareStatement(nodeSql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
                 PreparedStatement decrementStmt = conn.prepareStatement(decrementSql)) {
                nodeStmt.setString(1, UserContextManager.getTenant());
                nodeStmt.setArray(2, conn.createArrayOf("VARCHAR", uuids.toArray(new String[0])));
                decrementStmt.setString(1, UserContextManager.getTenant());
                try (ResultSet nodeRs = nodeStmt.executeQuery()) {
                    var removableContentRefs = new HashSet<String>();
                    while (nodeRs.next()) {
                        var uuid = nodeRs.getString("uuid");
                        var wipeable = nodeRs.getBoolean("wipeable");
                        var dataString = nodeRs.getString("data");
                        var removable = true;
                        if (StringUtils.isNotBlank(dataString)) {
                            try {
                                var data = objectMapper.readValue(dataString, NodeData.class);
                                if (wipeable) {
                                    for (var cp : data.getContents()) {
                                        if (cp != null && StringUtils.isNotBlank(cp.getContentUrl())) {
                                            decrementStmt.setString(2, cp.getContentUrl());
                                            decrementStmt.execute();
                                            try (var decrementRs = decrementStmt.getResultSet()) {
                                                if (decrementRs != null && decrementRs.next()) {
                                                    var counter = decrementRs.getInt("counter");
                                                    if (counter == 0) {
                                                        removableContentRefs.add(cp.getContentUrl());
                                                    }
                                                }
                                            }
                                        }
                                    } // end for
                                } else if (!data.getContents().isEmpty()) {
                                    removable = false;
                                    log.trace("Node {} cannot be cleaned: it is not 'wipeable' and it references {} contents", uuid, data.getContents().size());
                                }

                            } catch (JsonProcessingException e) {
                                //ignore
                                log.error("Unable to parse data of removed node '{}' (tenant {}): {}", uuid, UserContextManager.getTenant(), e.getMessage());
                            }
                        }

                        if (removable) {
                            nodeRs.deleteRow();
                            log.info("Node {} cleaned", uuid);
                        }
                    } // end while nodeRs

                    return removableContentRefs;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });

        var removableFileEntries = new ArrayList<String>();
        for (var item : items) {
            try {
                contentStoreService.delete(item);
                removableFileEntries.add(item);
            } catch (Exception e) {
                log.error("Unable to delete content file {}: {}", item, e.getMessage());
            }
        }

        var count = 0;
        if (!removableFileEntries.isEmpty()) {
            count = DBUtils.transactionCall(ds, UserContextManager.getContext().getDbSchema(), conn -> {
                var sql = """
                    delete from ecm_files\s
                    where tenant = ? and contentref = any(?) and counter < 1
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, UserContextManager.getTenant());
                    stmt.setArray(2, conn.createArrayOf("VARCHAR", removableFileEntries.toArray(new String[0])));
                    return stmt.executeUpdate();
                } catch (SQLException e) {
                    throw new SystemException(e);
                }
            });
        }

        log.info("{} file entries removed from tenant {}", count, UserContextManager.getTenant());
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}
