package co.rutamunicipios.domain.model;

import java.time.Instant;

/** Sesión autenticada. El token viaja en una cookie HttpOnly. */
public record Session(String token, UserId userId, Instant createdAt, Instant expiresAt) {
    public boolean isActive(Instant now) {
        return now.isBefore(expiresAt);
    }
}
