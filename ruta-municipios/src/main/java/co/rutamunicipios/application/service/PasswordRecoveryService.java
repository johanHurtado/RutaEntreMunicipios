package co.rutamunicipios.application.service;

import co.rutamunicipios.application.port.in.PasswordRecoveryUseCase;
import co.rutamunicipios.application.port.out.EmailSender;
import co.rutamunicipios.application.port.out.PasswordHasher;
import co.rutamunicipios.application.port.out.ResetTokenRepository;
import co.rutamunicipios.application.port.out.SessionRepository;
import co.rutamunicipios.application.port.out.TokenGenerator;
import co.rutamunicipios.application.port.out.UserRepository;
import co.rutamunicipios.domain.exception.DomainException;
import co.rutamunicipios.domain.exception.InvalidResetTokenException;
import co.rutamunicipios.domain.model.Email;
import co.rutamunicipios.domain.model.PasswordPolicy;
import co.rutamunicipios.domain.model.PasswordResetToken;
import co.rutamunicipios.domain.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Caso de uso HU-07.
 * 1) requestReset: genera token aleatorio, guarda solo su hash con vencimiento y envía el enlace.
 * 2) resetPassword: valida token (existe, no usado, no vencido), cambia el hash,
 *    marca el token como usado y cierra todas las sesiones abiertas del usuario.
 */
public final class PasswordRecoveryService implements PasswordRecoveryUseCase {

    private static final Logger LOG = Logger.getLogger(PasswordRecoveryService.class.getName());

    public record Settings(String baseUrl, Duration tokenTtl) {}

    private final UserRepository users;
    private final ResetTokenRepository resetTokens;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final TokenGenerator tokens;
    private final EmailSender mail;
    private final Clock clock;
    private final Settings settings;

    public PasswordRecoveryService(UserRepository users, ResetTokenRepository resetTokens, SessionRepository sessions,
                                   PasswordHasher hasher, TokenGenerator tokens, EmailSender mail,
                                   Clock clock, Settings settings) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.hasher = hasher;
        this.tokens = tokens;
        this.mail = mail;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public void requestReset(String rawEmail) {
        Optional<User> user;
        try {
            user = users.findByEmail(Email.of(rawEmail));
        } catch (DomainException invalid) {
            return; // misma respuesta para el cliente
        }
        user.ifPresent(this::issueTokenAndSend);
    }

    private void issueTokenAndSend(User user) {
        Instant now = clock.instant();
        String rawToken = tokens.newToken();
        resetTokens.invalidateAllFor(user.id()); // solo el último enlace es válido
        resetTokens.save(new PasswordResetToken(tokens.fingerprint(rawToken), user.id(), now,
                now.plus(settings.tokenTtl()), false));

        String link = settings.baseUrl() + "/restablecer?token=" + rawToken;
        long minutes = settings.tokenTtl().toMinutes();
        try {
            mail.send(EmailTemplates.passwordReset(user.email().value(), user.name(), link, minutes));
        } catch (RuntimeException e) {
            LOG.warning("No se pudo enviar el correo de recuperación: " + e.getMessage());
        }
    }

    @Override
    public boolean isTokenUsable(String rawToken) {
        return findUsable(rawToken).isPresent();
    }

    @Override
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = findUsable(rawToken).orElseThrow(InvalidResetTokenException::new);
        PasswordPolicy.validate(newPassword);

        User user = users.findById(token.userId()).orElseThrow(InvalidResetTokenException::new);
        user.changePassword(hasher.hash(newPassword), clock.instant()); // la anterior deja de servir
        users.save(user);

        resetTokens.save(token.markUsed());     // un solo uso
        resetTokens.invalidateAllFor(user.id());
        sessions.deleteAllFor(user.id());       // cierra sesiones abiertas con la contraseña vieja
    }

    private Optional<PasswordResetToken> findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) return Optional.empty();
        Instant now = clock.instant();
        return resetTokens.findByHash(tokens.fingerprint(rawToken)).filter(t -> t.isUsable(now));
    }
}
