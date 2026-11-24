package org.kivpn.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public class ProfileRepository {

    private final SqliteConnectionManager connections;

    public ProfileRepository(SqliteConnectionManager connections) {
        this.connections = connections;
    }

    public long add(Profile profile) throws SQLException {
        String sql = "INSERT INTO profiles(source_id, name, protocol, address, port, uri, params, active) "
                + "VALUES(?,?,?,?,?,?,?,?)";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, profile);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<Profile> findAll() throws SQLException {
        return query("SELECT * FROM profiles ORDER BY id");
    }

    public List<Profile> findBySource(long sourceId) throws SQLException {
        String sql = "SELECT * FROM profiles WHERE source_id = ? ORDER BY id";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return collect(rs);
            }
        }
    }

    public Optional<Profile> findById(long id) throws SQLException {
        String sql = "SELECT * FROM profiles WHERE id = ?";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Profile> findActive() throws SQLException {
        String sql = "SELECT * FROM profiles WHERE active = 1";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    public void update(Profile profile) throws SQLException {
        String sql = "UPDATE profiles SET source_id=?, name=?, protocol=?, address=?, port=?, uri=?, params=?, active=? "
                + "WHERE id=?";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, profile);
            ps.setLong(9, profile.id());
            ps.executeUpdate();
        }
    }

    public void setActive(long id) throws SQLException {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement clear = connection.prepareStatement("UPDATE profiles SET active = 0");
                 PreparedStatement set = connection.prepareStatement("UPDATE profiles SET active = 1 WHERE id = ?")) {
                clear.executeUpdate();
                set.setLong(1, id);
                set.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM profiles WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private List<Profile> query(String sql) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return collect(rs);
        }
    }

    private static List<Profile> collect(ResultSet rs) throws SQLException {
        List<Profile> result = new ArrayList<>();
        while (rs.next()) {
            result.add(map(rs));
        }
        return result;
    }

    private static void bind(PreparedStatement ps, Profile profile) throws SQLException {
        if (profile.sourceId() == null) {
            ps.setNull(1, Types.INTEGER);
        } else {
            ps.setLong(1, profile.sourceId());
        }
        ps.setString(2, profile.name());
        ps.setString(3, profile.protocol());
        ps.setString(4, profile.address());
        ps.setInt(5, profile.port());
        ps.setString(6, profile.uri());
        ps.setString(7, profile.params());
        ps.setInt(8, profile.active() ? 1 : 0);
    }

    private static Profile map(ResultSet rs) throws SQLException {
        long sourceId = rs.getLong("source_id");
        Long nullableSourceId = rs.wasNull() ? null : sourceId;
        return new Profile(rs.getLong("id"), nullableSourceId, rs.getString("name"), rs.getString("protocol"),
                rs.getString("address"), rs.getInt("port"), rs.getString("uri"), rs.getString("params"),
                rs.getInt("active") == 1);
    }
}
