package com.zaraki.exams.model;

import java.util.Objects;

public class RankingProfile {
    private Long id;
    private String profileName;
    private String description;
    private String rankingMethod;
    private int bestOfN;
    private boolean active;
    private String createdAt;
    private String updatedAt;

    public static final String METHOD_TOTAL_POINTS = "TOTAL_POINTS";
    public static final String METHOD_WEIGHTED_SUBJECTS = "WEIGHTED_SUBJECTS";
    public static final String METHOD_BEST_OF_N = "BEST_OF_N";

    public RankingProfile() {}

    public RankingProfile(String profileName, String description, String rankingMethod, int bestOfN, boolean active) {
        this.profileName = profileName;
        this.description = description;
        this.rankingMethod = rankingMethod;
        this.bestOfN = bestOfN;
        this.active = active;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRankingMethod() { return rankingMethod; }
    public void setRankingMethod(String rankingMethod) { this.rankingMethod = rankingMethod; }

    public int getBestOfN() { return bestOfN; }
    public void setBestOfN(int bestOfN) { this.bestOfN = bestOfN; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RankingProfile that)) return false;
        return bestOfN == that.bestOfN && active == that.active
            && Objects.equals(id, that.id)
            && Objects.equals(profileName, that.profileName)
            && Objects.equals(description, that.description)
            && Objects.equals(rankingMethod, that.rankingMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, profileName, description, rankingMethod, bestOfN, active);
    }
}
