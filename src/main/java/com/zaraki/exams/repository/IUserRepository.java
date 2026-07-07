package com.zaraki.exams.repository;

import java.util.List;
import java.util.Map;

/**
 * Repository for user accounts (admin/teacher login, password management).
 */
public interface IUserRepository {
    /** Looks up a user by username. Returns null if not found. */
    Map<String, Object> findByUsername(String username);
    /** Returns all users. */
    List<Map<String, Object>> findAll();
    /** Creates a new user with hashed password and salt. */
    void insert(String username, String passwordHash, String salt, String fullName, String role);
    /** Deletes a user by ID. */
    void delete(long userId);
    /** Resolves a username to a user ID. */
    long resolveUserId(String username);
    /** Updates the password hash and salt for a user. */
    void changePassword(long userId, String newHash, String newSalt);
}
