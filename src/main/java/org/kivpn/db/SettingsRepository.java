package org.kivpn.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SettingsRepository {

    private final SqliteConnectionManager connections;

    public SettingsRepository(SqliteConnectionManager connections) {
        this.connections = connections;
    }

    public Optional<String> get(String key) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void set(String key, String value) throws SQLException {
        String sql = "INSERT INTO settings(key, value) VALUES(?,?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
