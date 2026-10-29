package co.rutamunicipios.domain.model;

import java.time.Instant;

/**
 * Token de recuperación (HU-07). Solo se guarda el HASH del token,
 * nunca el valor enviado por correo; vence (30 min por defecto) y es de un solo uso.
 */
public record PasswordResetToken(String tokenHash, UserId userId, Instant createdAt, Instant expiresAt, boolean used) {

    public boolean isUsable(Instant now) {
        return !used && now.isBefore(expiresAt);
    }

    public PasswordResetToken markUsed() {
        return new PasswordResetToken(tokenHash, userId, createdAt, expiresAt, true);
    }
}
