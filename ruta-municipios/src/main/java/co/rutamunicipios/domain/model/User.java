package co.rutamunicipios.domain.model;

import co.rutamunicipios.domain.exception.InvalidNameException;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad raíz (Aggregate Root) del usuario.
 * Nunca guarda la contraseña en texto plano: solo su hash.
 */
public final class User {

    private final UserId id;
    private final String name;
    private final Email email;
    private String passwordHash;
    private final Instant createdAt;
    private Instant passwordChangedAt;
    private Origin origin;

    public User(UserId id, String name, Email email, String passwordHash,
                Instant createdAt, Instant passwordChangedAt, Origin origin) {
        this.id = Objects.requireNonNull(id);
        this.name = validateName(name);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.passwordChangedAt = passwordChangedAt;
        this.origin = origin;
    }

    /** Fábrica para una cuenta nueva (HU-01). */
    public static User register(String name, Email email, String passwordHash, Instant now) {
        return new User(UserId.newId(), name, email, passwordHash, now, now, null);
    }

    private static String validateName(String name) {
        if (name == null) throw new InvalidNameException();
        String clean = name.trim().replaceAll("\\s+", " ");
        if (clean.length() < 2 || clean.length() > 80) throw new InvalidNameException();
        return clean;
    }

    /** HU-07: al cambiar el hash, la contraseña anterior deja de funcionar. */
    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.passwordChangedAt = now;
    }

    /** HU-03: registra el municipio de origen elegido. */
    public void updateOrigin(Origin origin) {
        this.origin = Objects.requireNonNull(origin);
    }

    public UserId id() { return id; }
    public String name() { return name; }
    public Email email() { return email; }
    public String passwordHash() { return passwordHash; }
    public Instant createdAt() { return createdAt; }
    public Instant passwordChangedAt() { return passwordChangedAt; }
    public Origin origin() { return origin; }
}
