package com.zaraki.exams.config;

import com.zaraki.exams.database.DatabaseEngine;
import com.zaraki.exams.repository.SettingsRepository;

import java.sql.*;

public class SettingsManager {

    private final SettingsRepository repo;

    public SettingsManager() {
        this.repo = new SettingsRepository();
    }

    public CurriculumSystem getCurriculum() {
        String val = repo.get("curriculum", "SYSTEM_844");
        return CurriculumSystem.fromCode(val);
    }

    public void setCurriculum(CurriculumSystem system) {
        repo.set("curriculum", system.name());
    }

    public String getSchoolName() { return repo.get("school_name", "Kenya Secondary School"); }
    public void setSchoolName(String v) { repo.set("school_name", v); }

    public String getClosingDate() { return repo.get("closing_date", ""); }
    public void setClosingDate(String v) { repo.set("closing_date", v); }

    public String getOpeningDate() { return repo.get("opening_date", ""); }
    public void setOpeningDate(String v) { repo.set("opening_date", v); }

    public String getLogoPath() { return repo.get("logo_path", ""); }
    public void setLogoPath(String v) { repo.set("logo_path", v); }

    public String getStampPath() { return repo.get("stamp_path", ""); }
    public void setStampPath(String v) { repo.set("stamp_path", v); }

    public String getBestOfN() { return repo.get("best_of_n", "0"); }
    public void setBestOfN(String v) { repo.set("best_of_n", v); }

    public String getSetting(String key, String def) {
        return repo.get(key, def);
    }

    public void setSetting(String key, String value) {
        repo.set(key, value);
    }
}
