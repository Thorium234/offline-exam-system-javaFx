package com.zaraki.exams.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    void generateSalt_returnsNonEmptyBase64() {
        String salt = PasswordUtils.generateSalt();
        assertNotNull(salt);
        assertFalse(salt.isBlank());
        assertEquals(24, salt.length());
    }

    @Test
    void hashPassword_differentSaltsProduceDifferentHashes() {
        String password = "myPassword123";
        String salt1 = PasswordUtils.generateSalt();
        String salt2 = PasswordUtils.generateSalt();
        assertNotEquals(PasswordUtils.hashPassword(password, salt1), PasswordUtils.hashPassword(password, salt2));
    }

    @Test
    void hashPassword_sameSaltSamePasswordProduceSameHash() {
        String password = "testPassword";
        String salt = PasswordUtils.generateSalt();
        String hash1 = PasswordUtils.hashPassword(password, salt);
        String hash2 = PasswordUtils.hashPassword(password, salt);
        assertEquals(hash1, hash2);
    }

    @Test
    void verify_correctPasswordReturnsTrue() {
        String password = "correctPassword";
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);
        assertTrue(PasswordUtils.verify(password, salt, hash));
    }

    @Test
    void verify_wrongPasswordReturnsFalse() {
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("realPassword", salt);
        assertFalse(PasswordUtils.verify("wrongPassword", salt, hash));
    }

    @Test
    void verify_wrongSaltReturnsFalse() {
        String password = "testPassword";
        String salt1 = PasswordUtils.generateSalt();
        String salt2 = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt1);
        assertFalse(PasswordUtils.verify(password, salt2, hash));
    }
}
