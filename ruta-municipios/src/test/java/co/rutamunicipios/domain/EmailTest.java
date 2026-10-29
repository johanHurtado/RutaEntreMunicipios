package co.rutamunicipios.domain;

import co.rutamunicipios.domain.exception.InvalidEmailException;
import co.rutamunicipios.domain.model.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    @DisplayName("HU-01: normaliza mayúsculas y espacios")
    void normalizes() {
        assertEquals("johan@correo.com", Email.of("  Johan@Correo.COM ").value());
    }

    @Test
    @DisplayName("HU-01: rechaza formatos inválidos")
    void rejectsInvalid() {
        for (String bad : new String[]{"", "johan", "johan@", "@correo.com", "johan@correo", "jo han@correo.com", null}) {
            assertThrows(InvalidEmailException.class, () -> Email.of(bad), "debería rechazar: " + bad);
        }
    }

    @Test
    @DisplayName("HU-01: acepta formatos válidos comunes")
    void acceptsValid() {
        assertEquals("a.b+c@uni.edu.co", Email.of("a.b+c@uni.edu.co").value());
    }
}
