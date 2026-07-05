package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;

public interface IUserRepository {
    Map<String, Object> findByUsername(String username);
    List<Map<String, Object>> findAll();
    void insert(String username, String passwordHash, String salt, String fullName, String role);
    void delete(long userId);
    long resolveUserId(String username);
    void changePassword(long userId, String newHash, String newSalt);
}
