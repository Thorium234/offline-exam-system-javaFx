package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.model.GradingSystem;
import com.zaraki.exams.model.GradingSystemEntry;
import com.zaraki.exams.util.LoggerUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradingSystemRepositoryImpl implements IGradingSystemRepository {

    private final DatabaseEngine db;

    public GradingSystemRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
    }

    @Override
    public List<GradingSystem> findAll() {
        List<GradingSystem> list = new ArrayList<>();
        String sql = "SELECT * FROM grading_systems ORDER BY is_active DESC, system_name";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapSystem(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load grading systems", e);
        }
        return list;
    }

    @Override
    public GradingSystem findById(long id) {
        String sql = "SELECT * FROM grading_systems WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapSystem(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load grading system", e);
        }
    }

    @Override
    public GradingSystem findActive() {
        String sql = "SELECT * FROM grading_systems WHERE is_active = 1 LIMIT 1";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? mapSystem(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active grading system", e);
        }
    }

    @Override
    public long insertSystem(GradingSystem system) {
        String sql = "INSERT INTO grading_systems (system_name, description, is_active, created_at, updated_at) VALUES (?,?,?,?,?)";
        String now = LocalDateTime.now().toString();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, system.getSystemName());
            ps.setString(2, system.getDescription());
            ps.setInt(3, system.isActive() ? 1 : 0);
            ps.setString(4, now);
            ps.setString(5, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create grading system", e);
        }
    }

    @Override
    public void updateSystem(GradingSystem system) {
        String sql = "UPDATE grading_systems SET system_name=?, description=?, is_active=?, updated_at=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, system.getSystemName());
            ps.setString(2, system.getDescription());
            ps.setInt(3, system.isActive() ? 1 : 0);
            ps.setString(4, LocalDateTime.now().toString());
            ps.setLong(5, system.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update grading system", e);
        }
    }

    @Override
    public void deleteSystem(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM grading_systems WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete grading system", e);
        }
    }

    @Override
    public void setActive(long systemId) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("UPDATE grading_systems SET is_active = 0");
                 PreparedStatement ps2 = conn.prepareStatement("UPDATE grading_systems SET is_active = 1 WHERE id = ?")) {
                ps1.executeUpdate();
                ps2.setLong(1, systemId);
                ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set active grading system", e);
        }
    }

    @Override
    public List<GradingSystemEntry> findEntriesBySystem(long systemId) {
        List<GradingSystemEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM grading_system_entries WHERE system_id = ? ORDER BY subject_id NULLS LAST, points DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, systemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapEntry(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load grading entries", e);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> findEntriesWithSubject(long systemId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT gse.id, gse.system_id, gse.subject_id, gse.minimum_mark, gse.maximum_mark,
                   gse.grade, gse.points, gse.remarks,
                   COALESCE(sub.subject_name, '** Global **') AS subject_name
            FROM grading_system_entries gse
            LEFT JOIN subjects sub ON sub.id = gse.subject_id
            WHERE gse.system_id = ?
            ORDER BY gse.subject_id NULLS LAST, gse.points DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, systemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("system_id", rs.getLong("system_id"));
                row.put("subject_id", rs.getObject("subject_id"));
                row.put("minimum_mark", rs.getDouble("minimum_mark"));
                row.put("maximum_mark", rs.getDouble("maximum_mark"));
                row.put("grade", rs.getString("grade"));
                row.put("points", rs.getInt("points"));
                row.put("remarks", rs.getString("remarks"));
                row.put("subject_name", rs.getString("subject_name"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load grading entries", e);
        }
        return list;
    }

    @Override
    public long insertEntry(GradingSystemEntry entry) {
        String sql = "INSERT INTO grading_system_entries (system_id, subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, entry.getSystemId());
            if (entry.getSubjectId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setLong(2, entry.getSubjectId());
            ps.setDouble(3, entry.getMinimumMark());
            ps.setDouble(4, entry.getMaximumMark());
            ps.setString(5, entry.getGrade());
            ps.setInt(6, entry.getPoints());
            ps.setString(7, entry.getRemarks());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert grading entry", e);
        }
    }

    @Override
    public void updateEntry(GradingSystemEntry entry) {
        String sql = "UPDATE grading_system_entries SET subject_id=?, minimum_mark=?, maximum_mark=?, grade=?, points=?, remarks=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (entry.getSubjectId() == null) ps.setNull(1, Types.INTEGER);
            else ps.setLong(1, entry.getSubjectId());
            ps.setDouble(2, entry.getMinimumMark());
            ps.setDouble(3, entry.getMaximumMark());
            ps.setString(4, entry.getGrade());
            ps.setInt(5, entry.getPoints());
            ps.setString(6, entry.getRemarks());
            ps.setLong(7, entry.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update grading entry", e);
        }
    }

    @Override
    public void deleteEntry(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM grading_system_entries WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete grading entry", e);
        }
    }

    @Override
    public void insertBatchEntries(long systemId, List<GradingSystemEntry> entries) {
        String sql = "INSERT INTO grading_system_entries (system_id, subject_id, minimum_mark, maximum_mark, grade, points, remarks) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            try {
                for (GradingSystemEntry e : entries) {
                    ps.setLong(1, systemId);
                    if (e.getSubjectId() == null) ps.setNull(2, Types.INTEGER);
                    else ps.setLong(2, e.getSubjectId());
                    ps.setDouble(3, e.getMinimumMark());
                    ps.setDouble(4, e.getMaximumMark());
                    ps.setString(5, e.getGrade());
                    ps.setInt(6, e.getPoints());
                    ps.setString(7, e.getRemarks());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch insert grading entries", e);
        }
    }

    @Override
    public void cloneSystem(long sourceId, String newName) {
        GradingSystem source = findById(sourceId);
        if (source == null) throw new RuntimeException("Source system not found");

        GradingSystem clone = new GradingSystem(newName, "Cloned from " + source.getSystemName(), false);
        long newId = insertSystem(clone);

        List<GradingSystemEntry> entries = findEntriesBySystem(sourceId);
        List<GradingSystemEntry> cloned = new ArrayList<>();
        for (GradingSystemEntry e : entries) {
            GradingSystemEntry ce = new GradingSystemEntry(newId, e.getSubjectId(),
                e.getMinimumMark(), e.getMaximumMark(), e.getGrade(), e.getPoints(), e.getRemarks());
            cloned.add(ce);
        }
        insertBatchEntries(newId, cloned);
    }

    private GradingSystem mapSystem(ResultSet rs) throws SQLException {
        GradingSystem s = new GradingSystem();
        s.setId(rs.getLong("id"));
        s.setSystemName(rs.getString("system_name"));
        s.setDescription(rs.getString("description"));
        s.setActive(rs.getInt("is_active") == 1);
        s.setCreatedAt(rs.getString("created_at"));
        s.setUpdatedAt(rs.getString("updated_at"));
        return s;
    }

    private GradingSystemEntry mapEntry(ResultSet rs) throws SQLException {
        GradingSystemEntry e = new GradingSystemEntry();
        e.setId(rs.getLong("id"));
        e.setSystemId(rs.getLong("system_id"));
        long subjId = rs.getLong("subject_id");
        e.setSubjectId(rs.wasNull() ? null : subjId);
        e.setMinimumMark(rs.getDouble("minimum_mark"));
        e.setMaximumMark(rs.getDouble("maximum_mark"));
        e.setGrade(rs.getString("grade"));
        e.setPoints(rs.getInt("points"));
        e.setRemarks(rs.getString("remarks"));
        return e;
    }
}
