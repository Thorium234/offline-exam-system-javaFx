package com.zaraki.exams.model;

import java.util.Objects;

public class RankingProfileWeight {
    private Long id;
    private Long profileId;
    private Long subjectId;
    private double weight;

    public RankingProfileWeight() {}

    public RankingProfileWeight(Long profileId, Long subjectId, double weight) {
        this.profileId = profileId;
        this.subjectId = subjectId;
        this.weight = weight;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RankingProfileWeight that)) return false;
        return Double.compare(weight, that.weight) == 0
            && Objects.equals(id, that.id)
            && Objects.equals(profileId, that.profileId)
            && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, profileId, subjectId, weight);
    }
}
