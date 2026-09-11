package com.lanka.user;

import com.lanka.user.security.JwtUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwt = new JwtUtil("user_service_test_secret_key_0123456789_abcdefghijklmnop", 60_000L);

    @Test
    void generatesTokenCarryingRoleUidAndName() {
        String token = jwt.generateToken("kamal@gmail.com", "WORKER", 42L, "Kamal Perera");

        assertTrue(jwt.validateToken(token));
        assertEquals("kamal@gmail.com", jwt.extractUsername(token));
        assertEquals("WORKER", jwt.extractRole(token));
        assertEquals(42L, jwt.extractUid(token));
        assertEquals("Kamal Perera", jwt.extractName(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generateToken("kamal@gmail.com", "WORKER", 1L, "Kamal");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertFalse(jwt.validateToken(tampered));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtUtil other = new JwtUtil("a_completely_different_secret_key_0123456789_xyz", 60_000L);
        String foreign = other.generateToken("kamal@gmail.com", "ADMIN", 1L, "Kamal");

        assertFalse(jwt.validateToken(foreign));
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil("user_service_test_secret_key_0123456789_abcdefghijklmnop", 1L);
        String token = shortLived.generateToken("kamal@gmail.com", "WORKER", 1L, "Kamal");
        Thread.sleep(25L);

        assertFalse(shortLived.validateToken(token));
    }

    @Test
    void refusesWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short", 1000L));
    }
}
