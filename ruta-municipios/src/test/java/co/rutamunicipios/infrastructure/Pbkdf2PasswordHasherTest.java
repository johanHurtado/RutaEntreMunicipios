package co.rutamunicipios.infrastructure;

import co.rutamunicipios.infrastructure.adapter.out.security.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1_000);

    @Test
    @DisplayName("Misma contraseña produce hashes distintos (sal aleatoria) y ambos verifican")
    void saltedHashes() {
        String a = hasher.hash("Segura2026");
        String b = hasher.hash("Segura2026");
        assertNotEquals(a, b);
        assertTrue(hasher.matches("Segura2026", a));
        assertTrue(hasher.matches("Segura2026", b));
        assertFalse(hasher.matches("segura2026", a));
    }

    @Test
    @DisplayName("Hash corrupto o nulo nunca verifica")
    void corrupted() {
        assertFalse(hasher.matches("x", "basura"));
        assertFalse(hasher.matches("x", null));
        assertFalse(hasher.matches(null, hasher.hash("x")));
    }
}
