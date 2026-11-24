package org.kivpn.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.kivpn.model.Source;
import org.springframework.stereotype.Component;

@Component
public class SourceRepository {

    private final SqliteConnectionManager connections;

    public SourceRepository(SqliteConnectionManager connections) {
        this.connections = connections;
    }

    public long add(Source source) throws SQLException {
        String sql = "INSERT INTO sources(name, type, url, enabled) VALUES(?,?,?,?)";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, source.name());
            ps.setString(2, source.type());
            ps.setString(3, source.url());
            ps.setInt(4, source.enabled() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<Source> findAll() throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM sources ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<Source> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        }
    }

    public Optional<Source> findById(long id) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM sources WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void setEnabled(long id, boolean enabled) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement("UPDATE sources SET enabled = ? WHERE id = ?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM sources WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static Source map(ResultSet rs) throws SQLException {
        return new Source(rs.getLong("id"), rs.getString("name"), rs.getString("type"),
                rs.getString("url"), rs.getInt("enabled") == 1);
    }
}
