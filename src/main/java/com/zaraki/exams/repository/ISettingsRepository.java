package com.zaraki.exams.repository;

public interface ISettingsRepository {
    String get(String key, String def);
    void set(String key, String value);
}
