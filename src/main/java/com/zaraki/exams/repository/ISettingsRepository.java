package com.zaraki.exams.repository;

/**
 * Repository for application settings (key-value store).
 */
public interface ISettingsRepository {
    /** Retrieves a setting value, returning the default if not found. */
    String get(String key, String def);
    /** Sets a setting value (upsert). */
    void set(String key, String value);
}
