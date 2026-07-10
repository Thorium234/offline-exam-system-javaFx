package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.model.RankingProfile;
import com.zaraki.exams.model.RankingProfileWeight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankingProfileRepositoryTest extends DatabaseTestBase {

    private final IRankingProfileRepository repo = new RankingProfileRepositoryImpl();

    @Test
    void insertAndFindAll() {
        repo.insertProfile(new RankingProfile("P1", "desc1", RankingProfile.METHOD_TOTAL_POINTS, 0, false));
        repo.insertProfile(new RankingProfile("P2", "desc2", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 5, false));
        List<RankingProfile> all = repo.findAll();
        assertTrue(all.size() >= 2);
        assertTrue(all.stream().anyMatch(p -> "P1".equals(p.getProfileName())));
        assertTrue(all.stream().anyMatch(p -> "P2".equals(p.getProfileName())));
    }

    @Test
    void insertAndFindById() {
        long id = repo.insertProfile(new RankingProfile("FindMe", "desc", RankingProfile.METHOD_BEST_OF_N, 3, false));
        RankingProfile found = repo.findById(id);
        assertNotNull(found);
        assertEquals("FindMe", found.getProfileName());
        assertEquals("desc", found.getDescription());
        assertEquals(RankingProfile.METHOD_BEST_OF_N, found.getRankingMethod());
        assertEquals(3, found.getBestOfN());
        assertFalse(found.isActive());
    }

    @Test
    void updateProfile() {
        long id = repo.insertProfile(new RankingProfile("Old", "old", RankingProfile.METHOD_TOTAL_POINTS, 0, false));
        RankingProfile p = repo.findById(id);
        p.setProfileName("New");
        p.setRankingMethod(RankingProfile.METHOD_WEIGHTED_SUBJECTS);
        p.setBestOfN(5);
        repo.updateProfile(p);

        RankingProfile updated = repo.findById(id);
        assertEquals("New", updated.getProfileName());
        assertEquals(RankingProfile.METHOD_WEIGHTED_SUBJECTS, updated.getRankingMethod());
        assertEquals(5, updated.getBestOfN());
    }

    @Test
    void deleteProfile() {
        long id = repo.insertProfile(new RankingProfile("Del", "", RankingProfile.METHOD_TOTAL_POINTS, 0, false));
        assertNotNull(repo.findById(id));
        repo.deleteProfile(id);
        assertNull(repo.findById(id));
    }

    @Test
    void setActive_makesOnlyOneActive() {
        long id1 = repo.insertProfile(new RankingProfile("R1", "", RankingProfile.METHOD_TOTAL_POINTS, 0, false));
        long id2 = repo.insertProfile(new RankingProfile("R2", "", RankingProfile.METHOD_TOTAL_POINTS, 0, false));
        repo.setActive(id1);
        repo.setActive(id2);
        RankingProfile active = repo.findActive();
        assertNotNull(active);
        assertEquals(id2, active.getId());

        RankingProfile p1 = repo.findById(id1);
        assertFalse(p1.isActive());
    }

    @Test
    void findActive_returnsNullWhenNoneActive() {
        assertNull(repo.findActive());
    }

    @Test
    void insertAndFindWeights() {
        long profId = repo.insertProfile(new RankingProfile("W", "", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 0, false));
        long subj1 = insertSubject("MATH", "Math", "Math", "Compulsory");
        long subj2 = insertSubject("ENG", "English", "Lang", "Compulsory");

        repo.insertWeight(new RankingProfileWeight(profId, subj1, 2.0));
        repo.insertWeight(new RankingProfileWeight(profId, subj2, 1.5));

        List<RankingProfileWeight> weights = repo.findWeights(profId);
        assertEquals(2, weights.size());
    }

    @Test
    void findWeightsWithSubject_showsSubjectName() {
        long profId = repo.insertProfile(new RankingProfile("W", "", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 0, false));
        long subjId = insertSubject("PHY", "Physics", "Science", "Elective");
        repo.insertWeight(new RankingProfileWeight(profId, subjId, 3.0));

        List<java.util.Map<String, Object>> rows = repo.findWeightsWithSubject(profId);
        assertEquals(1, rows.size());
        assertEquals("Physics", rows.get(0).get("subject_name"));
        assertEquals(3.0, rows.get(0).get("weight"));
    }

    @Test
    void updateWeight() {
        long profId = repo.insertProfile(new RankingProfile("W", "", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 0, false));
        long subjId = insertSubject("CHEM", "Chemistry", "Science", "Elective");
        repo.insertWeight(new RankingProfileWeight(profId, subjId, 1.0));

        List<RankingProfileWeight> weights = repo.findWeights(profId);
        RankingProfileWeight w = weights.get(0);
        assertEquals(1.0, w.getWeight());
        w.setWeight(2.5);
        repo.updateWeight(w);

        List<RankingProfileWeight> updated = repo.findWeights(profId);
        assertEquals(2.5, updated.get(0).getWeight());
    }

    @Test
    void deleteWeight() {
        long profId = repo.insertProfile(new RankingProfile("W", "", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 0, false));
        long subjId = insertSubject("BIO", "Biology", "Science", "Elective");
        repo.insertWeight(new RankingProfileWeight(profId, subjId, 1.0));
        assertEquals(1, repo.findWeights(profId).size());

        List<RankingProfileWeight> weights = repo.findWeights(profId);
        repo.deleteWeight(weights.get(0).getId());
        assertEquals(0, repo.findWeights(profId).size());
    }

    @Test
    void replaceWeights_replacesAll() {
        long profId = repo.insertProfile(new RankingProfile("W", "", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 0, false));
        long subj1 = insertSubject("M1", "Math", "Math", "Compulsory");
        long subj2 = insertSubject("E1", "English", "Lang", "Compulsory");
        long subj3 = insertSubject("S1", "Science", "Science", "Compulsory");

        repo.insertWeight(new RankingProfileWeight(profId, subj1, 1.0));
        repo.insertWeight(new RankingProfileWeight(profId, subj2, 1.0));
        assertEquals(2, repo.findWeights(profId).size());

        List<RankingProfileWeight> newWeights = List.of(
            new RankingProfileWeight(profId, subj3, 2.0),
            new RankingProfileWeight(profId, subj1, 1.5)
        );
        repo.replaceWeights(profId, newWeights);

        List<RankingProfileWeight> result = repo.findWeights(profId);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(w -> w.getSubjectId() == subj2));
        assertTrue(result.stream().anyMatch(w -> w.getSubjectId() == subj3 && w.getWeight() == 2.0));
    }

    @Test
    void findAll_empty() {
        List<RankingProfile> all = repo.findAll();
        assertTrue(all.isEmpty());
    }

    @Test
    void setActive_onNonexistent_throwsNoException() {
        assertDoesNotThrow(() -> repo.setActive(99999));
    }

    @Test
    void deleteProfile_alsoCascadesWeights() {
        long profId = repo.insertProfile(new RankingProfile("W", "", RankingProfile.METHOD_WEIGHTED_SUBJECTS, 0, false));
        long subjId = insertSubject("M1", "Math", "Math", "Compulsory");
        repo.insertWeight(new RankingProfileWeight(profId, subjId, 2.0));
        assertEquals(1, repo.findWeights(profId).size());

        repo.deleteProfile(profId);
        assertEquals(0, repo.findWeights(profId).size());
    }
}
