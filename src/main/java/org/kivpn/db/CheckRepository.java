package org.kivpn.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.kivpn.model.CheckResult;
import org.springframework.stereotype.Component;

@Component
public class CheckRepository {

    private final SqliteConnectionManager connections;

    public CheckRepository(SqliteConnectionManager connections) {
        this.connections = connections;
    }

    public long add(CheckResult check) throws SQLException {
        String sql = "INSERT INTO checks(profile_id, status, latency_ms, checked_at, error) VALUES(?,?,?,?,?)";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, check.profileId());
            ps.setString(2, check.status());
            if (check.latencyMs() == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, check.latencyMs());
            }
            ps.setString(4, check.checkedAt());
            ps.setString(5, check.error());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<CheckResult> findLatest(long profileId) throws SQLException {
        String sql = "SELECT * FROM checks WHERE profile_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Map<Long, CheckResult> findLatestPerProfile() throws SQLException {
        String sql = "SELECT c.* FROM checks c "
                + "JOIN (SELECT profile_id, MAX(id) AS last_id FROM checks GROUP BY profile_id) t ON c.id = t.last_id";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<Long, CheckResult> result = new HashMap<>();
            while (rs.next()) {
                CheckResult check = map(rs);
                result.put(check.profileId(), check);
            }
            return result;
        }
    }

    private static CheckResult map(ResultSet rs) throws SQLException {
        int latency = rs.getInt("latency_ms");
        Integer nullableLatency = rs.wasNull() ? null : latency;
        return new CheckResult(rs.getLong("id"), rs.getLong("profile_id"), rs.getString("status"),
                nullableLatency, rs.getString("checked_at"), rs.getString("error"));
    }
}
