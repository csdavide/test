package it.doqui.libra.librabl.utils;

import it.doqui.libra.librabl.foundation.exceptions.SystemException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLScriptUtils {

    private SQLScriptUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static final Pattern COMMENT_PATTERN = Pattern.compile("--\\s.*|/\\*(.|[\\r\\n])*?\\*/");

    public static List<String> parseSQLScript(Path scriptFilePath) {
        List<String> sqlStatements = new ArrayList<>();

        var block = false;
        try (var reader = Files.newBufferedReader(scriptFilePath)) {
            StringBuilder currentStatement = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher commentMatcher = COMMENT_PATTERN.matcher(line);
                line = commentMatcher.replaceAll("");

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("$$")) {
                    block = !block;
                }

                currentStatement.append(line).append(" ");

                if (!block && line.endsWith(";")) {
                    sqlStatements.add(currentStatement.toString());
                    currentStatement.setLength(0);
                }
            }
        } catch (IOException e) {
            throw new SystemException(e);
        }

        return sqlStatements;
    }

    public static void executeSQLBatches(Connection connection, List<String> sqlStatements, int batchSize) throws SQLException {
        int count = 0;
        try (var statement = connection.createStatement()) {
            for (var sql : sqlStatements) {
                statement.addBatch(sql);
                count++;

                if (count % batchSize == 0) {
                    statement.executeBatch();
                    statement.clearBatch();
                }
            }
            if (count % batchSize != 0) {
                statement.executeBatch();
            }
        }
    }
}
