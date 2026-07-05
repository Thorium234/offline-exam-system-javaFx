package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRepositoryTest extends DatabaseTestBase {

    private final ISettingsRepository repo = new SettingsRepositoryImpl();

    @Test
    void get_returnsDefaultForMissingKey() {
        assertEquals("default_val", repo.get("nonexistent_key", "default_val"));
    }

    @Test
    void setAndGet() {
        repo.set("test_key", "test_value");
        assertEquals("test_value", repo.get("test_key", "default"));
    }

    @Test
    void set_overwritesExisting() {
        repo.set("test_key", "value1");
        repo.set("test_key", "value2");
        assertEquals("value2", repo.get("test_key", "default"));
    }

    @Test
    void get_returnsDefaultOnException() {
        assertEquals("fallback", repo.get(null, "fallback"));
    }

    @Test
    void defaultSettingsExist() {
        assertEquals("SYSTEM_844", repo.get("curriculum", ""));
        assertEquals("Kenya Secondary School", repo.get("school_name", ""));
    }

    @Test
    void setEmptyString() {
        repo.set("test_key", "");
        assertEquals("", repo.get("test_key", "default"));
    }

    @Test
    void get_returnsDefaultWhenValueIsNull() {
        repo.set("test_key", null);
        String val = repo.get("test_key", "fallback");
        assertEquals("fallback", val);
    }
}
