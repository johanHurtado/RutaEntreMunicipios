package co.rutamunicipios.support;

import co.rutamunicipios.infrastructure.adapter.out.security.Pbkdf2PasswordHasher;

/** PBKDF2 real pero con pocas iteraciones para que las pruebas corran rápido. */
public final class FastHasher {
    private FastHasher() {}
    public static Pbkdf2PasswordHasher create() { return new Pbkdf2PasswordHasher(1_000); }
}
