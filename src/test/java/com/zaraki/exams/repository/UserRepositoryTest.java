package com.zaraki.exams.repository;

import com.zaraki.exams.DatabaseTestBase;
import com.zaraki.exams.auth.PasswordUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest extends DatabaseTestBase {

    private final IUserRepository repo = new UserRepositoryImpl();

    @Test
    void insertAndFindByUsername() {
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("test123", salt);
        repo.insert("testuser", hash, salt, "Test User", "teacher");

        Map<String, Object> found = repo.findByUsername("testuser");
        assertNotNull(found);
        assertEquals("testuser", found.get("username"));
        assertEquals("Test User", found.get("full_name"));
        assertEquals("teacher", found.get("role"));
    }

    @Test
    void findByUsername_returnsNullForMissing() {
        assertNull(repo.findByUsername("nonexistent"));
    }

    @Test
    void findAll() {
        String salt1 = PasswordUtils.generateSalt();
        String salt2 = PasswordUtils.generateSalt();
        repo.insert("user1", PasswordUtils.hashPassword("pass", salt1), salt1, "User One", "teacher");
        repo.insert("user2", PasswordUtils.hashPassword("pass", salt2), salt2, "User Two", "admin");
        var all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void delete() {
        String salt = PasswordUtils.generateSalt();
        repo.insert("testuser", PasswordUtils.hashPassword("pass", salt), salt, "Test", "teacher");
        assertNotNull(repo.findByUsername("testuser"));
        repo.delete(1L);
        assertNull(repo.findByUsername("testuser"));
    }

    @Test
    void delete_doesNotDeleteAdmin() {
        assertTrue(repo.findByUsername("admin") != null);
        repo.delete(1L);
        assertNotNull(repo.findByUsername("admin"));
    }

    @Test
    void resolveUserId() {
        String salt = PasswordUtils.generateSalt();
        repo.insert("testuser", PasswordUtils.hashPassword("pass", salt), salt, "Test", "teacher");
        long id = repo.resolveUserId("testuser");
        assertTrue(id > 0);
        assertEquals(0, repo.resolveUserId("nonexistent"));
    }

    @Test
    void changePassword() {
        String salt1 = PasswordUtils.generateSalt();
        String hash1 = PasswordUtils.hashPassword("oldpass", salt1);
        repo.insert("testuser", hash1, salt1, "Test User", "teacher");
        long userId = repo.resolveUserId("testuser");

        String salt2 = PasswordUtils.generateSalt();
        String hash2 = PasswordUtils.hashPassword("newpass", salt2);
        repo.changePassword(userId, hash2, salt2);

        Map<String, Object> found = repo.findByUsername("testuser");
        assertEquals(hash2, found.get("password_hash"));
        assertEquals(salt2, found.get("salt"));
    }

    @Test
    void duplicateUsername_throws() {
        String salt = PasswordUtils.generateSalt();
        repo.insert("testuser", "hash", salt, "Test", "teacher");
        assertThrows(RuntimeException.class, () -> repo.insert("testuser", "hash2", salt, "Test2", "teacher"));
    }
}
