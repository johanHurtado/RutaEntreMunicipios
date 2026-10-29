package co.rutamunicipios.application;

import co.rutamunicipios.application.port.in.AuthenticationUseCase.LoginCommand;
import co.rutamunicipios.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import co.rutamunicipios.application.service.AuthenticationService;
import co.rutamunicipios.application.service.PasswordRecoveryService;
import co.rutamunicipios.application.service.RegisterUserService;
import co.rutamunicipios.domain.exception.InvalidCredentialsException;
import co.rutamunicipios.domain.exception.InvalidResetTokenException;
import co.rutamunicipios.domain.exception.WeakPasswordException;
import co.rutamunicipios.domain.model.PasswordResetToken;
import co.rutamunicipios.domain.model.UserId;
import co.rutamunicipios.application.port.out.ResetTokenRepository;
import co.rutamunicipios.infrastructure.adapter.out.persistence.InMemorySessionRepository;
import co.rutamunicipios.infrastructure.adapter.out.security.SecureTokenGenerator;
import co.rutamunicipios.support.FastHasher;
import co.rutamunicipios.support.InMemoryUserRepository;
import co.rutamunicipios.support.MutableClock;
import co.rutamunicipios.support.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryServiceTest {

    private MutableClock clock;
    private RecordingEmailSender mail;
    private PasswordRecoveryService recovery;
    private AuthenticationService auth;
    private InMemorySessionRepository sessions;

    /** Repositorio en memoria del puerto ResetTokenRepository. */
    static final class MemTokens implements ResetTokenRepository {
        final Map<String, PasswordResetToken> data = new HashMap<>();
        public void save(PasswordResetToken t) { data.put(t.tokenHash(), t); }
        public Optional<PasswordResetToken> findByHash(String h) { return Optional.ofNullable(data.get(h)); }
        public void invalidateAllFor(UserId id) { data.replaceAll((k, t) -> t.userId().equals(id) ? t.markUsed() : t); }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-21T12:00:00Z"));
        mail = new RecordingEmailSender();
        sessions = new InMemorySessionRepository();
        var users = new InMemoryUserRepository();
        var hasher = FastHasher.create();
        var tokens = new SecureTokenGenerator();
        new RegisterUserService(users, hasher, clock)
                .register(new RegisterUserCommand("Diego", "diego@correo.com", "Vieja2026"));
        auth = new AuthenticationService(users, hasher, sessions, tokens, clock, Duration.ofHours(2));
        recovery = new PasswordRecoveryService(users, new MemTokens(), sessions, hasher, tokens, mail, clock,
                new PasswordRecoveryService.Settings("http://localhost:8080", Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("HU-07 éxito: llega el correo, se cambia la clave y la ANTERIOR deja de funcionar")
    void fullReset() {
        var oldSession = auth.login(new LoginCommand("diego@correo.com", "Vieja2026")).sessionToken();

        recovery.requestReset("diego@correo.com");
        assertEquals(1, mail.sent.size());
        assertTrue(mail.sent.get(0).textBody().contains("http://localhost:8080/restablecer?token="));

        recovery.resetPassword(mail.lastToken(), "Nueva2026");

        assertThrows(InvalidCredentialsException.class,
                () -> auth.login(new LoginCommand("diego@correo.com", "Vieja2026")));
        assertNotNull(auth.login(new LoginCommand("diego@correo.com", "Nueva2026")).sessionToken());
        assertTrue(auth.currentUser(oldSession).isEmpty(), "las sesiones viejas se cierran");
    }

    @Test
    @DisplayName("HU-07 límite: a los 29:59 funciona; a los 30:00 el enlace está vencido")
    void expiresAtThirtyMinutes() {
        recovery.requestReset("diego@correo.com");
        String token = mail.lastToken();

        clock.advance(Duration.ofMinutes(29).plusSeconds(59));
        assertTrue(recovery.isTokenUsable(token));

        clock.advance(Duration.ofSeconds(1));
        assertFalse(recovery.isTokenUsable(token));
        assertThrows(InvalidResetTokenException.class, () -> recovery.resetPassword(token, "Nueva2026"));
        // la contraseña original sigue intacta
        assertNotNull(auth.login(new LoginCommand("diego@correo.com", "Vieja2026")));
    }

    @Test
    @DisplayName("HU-07: el enlace es de un solo uso y solo vale el último solicitado")
    void singleUseAndLatestOnly() {
        recovery.requestReset("diego@correo.com");
        String first = mail.lastToken();
        recovery.requestReset("diego@correo.com");
        String second = mail.lastToken();

        assertThrows(InvalidResetTokenException.class, () -> recovery.resetPassword(first, "Nueva2026"));
        recovery.resetPassword(second, "Nueva2026");
        assertThrows(InvalidResetTokenException.class, () -> recovery.resetPassword(second, "Otra20266"));
    }

    @Test
    @DisplayName("HU-07: correo inexistente no envía nada ni revela si existe")
    void unknownEmail() {
        recovery.requestReset("nadie@correo.com");
        recovery.requestReset("no-es-correo");
        assertTrue(mail.sent.isEmpty());
    }

    @Test
    @DisplayName("HU-07: la nueva contraseña también exige 8 caracteres")
    void newPasswordPolicy() {
        recovery.requestReset("diego@correo.com");
        assertThrows(WeakPasswordException.class, () -> recovery.resetPassword(mail.lastToken(), "corta"));
    }
}
