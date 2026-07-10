package com.zaraki.exams.repository;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.model.RankingProfile;
import com.zaraki.exams.model.RankingProfileWeight;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RankingProfileRepositoryImpl implements IRankingProfileRepository {

    private final DatabaseEngine db;

    public RankingProfileRepositoryImpl() {
        this.db = DatabaseEngine.getInstance();
    }

    @Override
    public List<RankingProfile> findAll() {
        List<RankingProfile> list = new ArrayList<>();
        String sql = "SELECT * FROM ranking_profiles ORDER BY is_active DESC, profile_name";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapProfile(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load ranking profiles", e);
        }
        return list;
    }

    @Override
    public RankingProfile findById(long id) {
        String sql = "SELECT * FROM ranking_profiles WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapProfile(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load ranking profile", e);
        }
    }

    @Override
    public RankingProfile findActive() {
        String sql = "SELECT * FROM ranking_profiles WHERE is_active = 1 LIMIT 1";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? mapProfile(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active ranking profile", e);
        }
    }

    @Override
    public long insertProfile(RankingProfile profile) {
        String sql = "INSERT INTO ranking_profiles (profile_name, description, ranking_method, best_of_n, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?)";
        String now = LocalDateTime.now().toString();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, profile.getProfileName());
            ps.setString(2, profile.getDescription());
            ps.setString(3, profile.getRankingMethod());
            ps.setInt(4, profile.getBestOfN());
            ps.setInt(5, profile.isActive() ? 1 : 0);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ranking profile", e);
        }
    }

    @Override
    public void updateProfile(RankingProfile profile) {
        String sql = "UPDATE ranking_profiles SET profile_name=?, description=?, ranking_method=?, best_of_n=?, is_active=?, updated_at=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getProfileName());
            ps.setString(2, profile.getDescription());
            ps.setString(3, profile.getRankingMethod());
            ps.setInt(4, profile.getBestOfN());
            ps.setInt(5, profile.isActive() ? 1 : 0);
            ps.setString(6, LocalDateTime.now().toString());
            ps.setLong(7, profile.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update ranking profile", e);
        }
    }

    @Override
    public void deleteProfile(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ranking_profiles WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete ranking profile", e);
        }
    }

    @Override
    public void setActive(long profileId) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("UPDATE ranking_profiles SET is_active = 0");
                 PreparedStatement ps2 = conn.prepareStatement("UPDATE ranking_profiles SET is_active = 1 WHERE id = ?")) {
                ps1.executeUpdate();
                ps2.setLong(1, profileId);
                ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set active ranking profile", e);
        }
    }

    @Override
    public List<RankingProfileWeight> findWeights(long profileId) {
        List<RankingProfileWeight> list = new ArrayList<>();
        String sql = "SELECT * FROM ranking_profile_weights WHERE profile_id = ? ORDER BY weight DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, profileId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapWeight(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load ranking weights", e);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> findWeightsWithSubject(long profileId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT rpw.id, rpw.profile_id, rpw.subject_id, rpw.weight, sub.subject_name
            FROM ranking_profile_weights rpw
            JOIN subjects sub ON sub.id = rpw.subject_id
            WHERE rpw.profile_id = ?
            ORDER BY rpw.weight DESC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, profileId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("profile_id", rs.getLong("profile_id"));
                row.put("subject_id", rs.getLong("subject_id"));
                row.put("weight", rs.getDouble("weight"));
                row.put("subject_name", rs.getString("subject_name"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load ranking weights", e);
        }
        return list;
    }

    @Override
    public void insertWeight(RankingProfileWeight weight) {
        String sql = "INSERT INTO ranking_profile_weights (profile_id, subject_id, weight) VALUES (?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, weight.getProfileId());
            ps.setLong(2, weight.getSubjectId());
            ps.setDouble(3, weight.getWeight());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert ranking weight", e);
        }
    }

    @Override
    public void updateWeight(RankingProfileWeight weight) {
        String sql = "UPDATE ranking_profile_weights SET weight=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, weight.getWeight());
            ps.setLong(2, weight.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update ranking weight", e);
        }
    }

    @Override
    public void deleteWeight(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ranking_profile_weights WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete ranking weight", e);
        }
    }

    @Override
    public void replaceWeights(long profileId, List<RankingProfileWeight> weights) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM ranking_profile_weights WHERE profile_id = ?");
                 PreparedStatement ins = conn.prepareStatement("INSERT INTO ranking_profile_weights (profile_id, subject_id, weight) VALUES (?,?,?)")) {
                del.setLong(1, profileId);
                del.executeUpdate();
                for (RankingProfileWeight w : weights) {
                    ins.setLong(1, profileId);
                    ins.setLong(2, w.getSubjectId());
                    ins.setDouble(3, w.getWeight());
                    ins.addBatch();
                }
                ins.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to replace ranking weights", e);
        }
    }

    private RankingProfile mapProfile(ResultSet rs) throws SQLException {
        RankingProfile p = new RankingProfile();
        p.setId(rs.getLong("id"));
        p.setProfileName(rs.getString("profile_name"));
        p.setDescription(rs.getString("description"));
        p.setRankingMethod(rs.getString("ranking_method"));
        p.setBestOfN(rs.getInt("best_of_n"));
        p.setActive(rs.getInt("is_active") == 1);
        p.setCreatedAt(rs.getString("created_at"));
        p.setUpdatedAt(rs.getString("updated_at"));
        return p;
    }

    private RankingProfileWeight mapWeight(ResultSet rs) throws SQLException {
        RankingProfileWeight w = new RankingProfileWeight();
        w.setId(rs.getLong("id"));
        w.setProfileId(rs.getLong("profile_id"));
        w.setSubjectId(rs.getLong("subject_id"));
        w.setWeight(rs.getDouble("weight"));
        return w;
    }
}
