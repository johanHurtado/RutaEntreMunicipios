package co.rutamunicipios.infrastructure;

import co.rutamunicipios.domain.model.Email;
import co.rutamunicipios.domain.model.Origin;
import co.rutamunicipios.domain.model.User;
import co.rutamunicipios.infrastructure.adapter.out.persistence.JsonFileUserRepository;
import co.rutamunicipios.support.FastHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JsonFileUserRepositoryTest {

    @Test
    @DisplayName("HU-01 persistencia: tras 'reiniciar' (nueva instancia) la cuenta sigue existiendo")
    void survivesRestart() throws Exception {
        Path file = Files.createTempDirectory("rem").resolve("users.json");
        String hash = FastHasher.create().hash("Segura2026");

        User u = User.register("Johan Hurtado", Email.of("johan@correo.com"), hash, Instant.now());
        u.updateOrigin(Origin.manual("Tuluá", Instant.now()));
        new JsonFileUserRepository(file).save(u);

        JsonFileUserRepository reloaded = new JsonFileUserRepository(file);
        User back = reloaded.findByEmail(Email.of("JOHAN@correo.com")).orElseThrow();
        assertEquals("Johan Hurtado", back.name());
        assertEquals(hash, back.passwordHash());
        assertEquals("Tuluá", back.origin().municipality());

        String raw = Files.readString(file);
        assertFalse(raw.contains("Segura2026"), "el archivo NO debe contener la contraseña");
        assertTrue(raw.contains("pbkdf2_sha256$"));
    }
}
