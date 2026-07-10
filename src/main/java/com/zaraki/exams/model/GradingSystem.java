package com.zaraki.exams.model;

import java.util.Objects;

public class GradingSystem {
    private Long id;
    private String systemName;
    private String description;
    private boolean active;
    private String createdAt;
    private String updatedAt;

    public GradingSystem() {}

    public GradingSystem(String systemName, String description, boolean active) {
        this.systemName = systemName;
        this.description = description;
        this.active = active;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GradingSystem that)) return false;
        return active == that.active && Objects.equals(id, that.id)
            && Objects.equals(systemName, that.systemName)
            && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, systemName, description, active);
    }
}
