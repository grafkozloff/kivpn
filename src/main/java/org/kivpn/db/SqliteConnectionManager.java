package org.kivpn.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SqliteConnectionManager {

    private final Path dbPath;

    public SqliteConnectionManager(@Value("${kivpn.db.path:data/kivpn.db}") String dbPath) {
        this.dbPath = Path.of(dbPath);
    }

    public Path dbPath() {
        return dbPath;
    }

    public Connection open() throws SQLException {
        Path parent = dbPath.toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SQLException("Не удалось создать каталог данных " + parent + ": " + e.getMessage(), e);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
