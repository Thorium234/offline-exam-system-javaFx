package com.zaraki.exams.repository;

import com.zaraki.exams.model.RankingProfile;
import com.zaraki.exams.model.RankingProfileWeight;

import java.util.List;
import java.util.Map;

public interface IRankingProfileRepository {

    List<RankingProfile> findAll();

    RankingProfile findById(long id);

    RankingProfile findActive();

    long insertProfile(RankingProfile profile);

    void updateProfile(RankingProfile profile);

    void deleteProfile(long id);

    void setActive(long profileId);

    List<RankingProfileWeight> findWeights(long profileId);

    List<Map<String, Object>> findWeightsWithSubject(long profileId);

    void insertWeight(RankingProfileWeight weight);

    void updateWeight(RankingProfileWeight weight);

    void deleteWeight(long id);

    void replaceWeights(long profileId, List<RankingProfileWeight> weights);
}
