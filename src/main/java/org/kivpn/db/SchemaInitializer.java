package org.kivpn.db;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer {

    private record Migration(int version, List<String> statements) {
    }

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, List.of(
                    """
                    CREATE TABLE sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        url TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )""",
                    """
                    CREATE TABLE profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        source_id INTEGER REFERENCES sources(id) ON DELETE SET NULL,
                        name TEXT NOT NULL,
                        protocol TEXT NOT NULL,
                        address TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        params TEXT,
                        active INTEGER NOT NULL DEFAULT 0
                    )""",
                    """
                    CREATE TABLE checks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                        status TEXT NOT NULL,
                        latency_ms INTEGER,
                        checked_at TEXT NOT NULL,
                        error TEXT
                    )""",
                    "CREATE INDEX idx_profiles_source ON profiles(source_id)",
                    "CREATE INDEX idx_checks_profile ON checks(profile_id)")));

    private final SqliteConnectionManager connections;

    public SchemaInitializer(SqliteConnectionManager connections) {
        this.connections = connections;
    }

    @PostConstruct
    public void init() {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                int current;
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
                    current = rs.next() ? rs.getInt(1) : 0;
                }
                for (Migration migration : MIGRATIONS) {
                    if (migration.version() <= current) {
                        continue;
                    }
                    try (Statement statement = connection.createStatement()) {
                        for (String sql : migration.statements()) {
                            statement.execute(sql);
                        }
                        statement.execute("PRAGMA user_version = " + migration.version());
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось инициализировать схему базы данных: " + e.getMessage(), e);
        }
    }
}
