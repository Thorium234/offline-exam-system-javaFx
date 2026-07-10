package com.zaraki.exams.repository;

import com.zaraki.exams.model.GradingSystem;
import com.zaraki.exams.model.GradingSystemEntry;

import java.util.List;
import java.util.Map;

public interface IGradingSystemRepository {

    List<GradingSystem> findAll();

    GradingSystem findById(long id);

    GradingSystem findActive();

    long insertSystem(GradingSystem system);

    void updateSystem(GradingSystem system);

    void deleteSystem(long id);

    void setActive(long systemId);

    List<GradingSystemEntry> findEntriesBySystem(long systemId);

    List<Map<String, Object>> findEntriesWithSubject(long systemId);

    long insertEntry(GradingSystemEntry entry);

    void updateEntry(GradingSystemEntry entry);

    void deleteEntry(long id);

    void insertBatchEntries(long systemId, List<GradingSystemEntry> entries);

    void cloneSystem(long sourceId, String newName);
}
