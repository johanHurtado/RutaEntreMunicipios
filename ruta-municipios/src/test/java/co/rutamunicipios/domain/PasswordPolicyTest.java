package co.rutamunicipios.domain;

import co.rutamunicipios.domain.exception.WeakPasswordException;
import co.rutamunicipios.domain.model.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    @DisplayName("HU-01 límite: 7 caracteres se rechaza, 8 se acepta")
    void minimumLengthBoundary() {
        assertThrows(WeakPasswordException.class, () -> PasswordPolicy.validate("1234567"));
        PasswordPolicy.validate("12345678");
    }

    @Test
    @DisplayName("HU-01: contraseña nula o solo espacios se rechaza")
    void nullOrBlank() {
        assertThrows(WeakPasswordException.class, () -> PasswordPolicy.validate(null));
        assertThrows(WeakPasswordException.class, () -> PasswordPolicy.validate("        "));
    }
}
