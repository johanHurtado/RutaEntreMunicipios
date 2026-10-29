package co.rutamunicipios.domain.model;

import co.rutamunicipios.domain.exception.WeakPasswordException;

/** Regla de negocio de contraseñas (HU-01 y HU-07): mínimo 8 caracteres. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new WeakPasswordException("La contraseña debe tener mínimo " + MIN_LENGTH + " caracteres.");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new WeakPasswordException("La contraseña no puede superar " + MAX_LENGTH + " caracteres.");
        }
        if (rawPassword.isBlank()) {
            throw new WeakPasswordException("La contraseña no puede estar compuesta solo por espacios.");
        }
    }
}
