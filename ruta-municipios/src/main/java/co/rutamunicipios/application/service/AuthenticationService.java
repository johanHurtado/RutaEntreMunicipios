package co.rutamunicipios.application.service;

import co.rutamunicipios.application.port.in.AuthenticationUseCase;
import co.rutamunicipios.application.port.in.UserView;
import co.rutamunicipios.application.port.out.PasswordHasher;
import co.rutamunicipios.application.port.out.SessionRepository;
import co.rutamunicipios.application.port.out.TokenGenerator;
import co.rutamunicipios.application.port.out.UserRepository;
import co.rutamunicipios.domain.exception.DomainException;
import co.rutamunicipios.domain.exception.InvalidCredentialsException;
import co.rutamunicipios.domain.model.Email;
import co.rutamunicipios.domain.model.Session;
import co.rutamunicipios.domain.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Caso de uso HU-02: inicio de sesión, cierre y validación de sesión. */
public final class AuthenticationService implements AuthenticationUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionRepository sessions;
    private final TokenGenerator tokens;
    private final Clock clock;
    private final Duration sessionTtl;
    /** Hash señuelo: se compara aunque el correo no exista para igualar tiempos de respuesta. */
    private final String decoyHash;

    public AuthenticationService(UserRepository users, PasswordHasher hasher, SessionRepository sessions,
                                 TokenGenerator tokens, Clock clock, Duration sessionTtl) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
        this.decoyHash = hasher.hash("decoy-password-not-used");
    }

    @Override
    public LoginResult login(LoginCommand command) {
        Optional<User> found = findUser(command.email());
        String password = command.password() == null ? "" : command.password();

        // Siempre se ejecuta una verificación de hash (exista o no el usuario).
        boolean valid = found.map(u -> hasher.matches(password, u.passwordHash()))
                             .orElseGet(() -> { hasher.matches(password, decoyHash); return false; });
        if (!valid) {
            throw new InvalidCredentialsException(); // error genérico, no dice qué falló
        }

        User user = found.get();
        Instant now = clock.instant();
        Session session = new Session(tokens.newToken(), user.id(), now, now.plus(sessionTtl));
        sessions.save(session);
        return new LoginResult(session.token(), sessionTtl.toSeconds(), UserView.from(user));
    }

    private Optional<User> findUser(String rawEmail) {
        try {
            return users.findByEmail(Email.of(rawEmail));
        } catch (DomainException invalidFormat) {
            return Optional.empty(); // un correo mal escrito también es "credenciales inválidas"
        }
    }

    @Override
    public void logout(String sessionToken) {
        if (sessionToken != null) sessions.delete(sessionToken);
    }

    @Override
    public Optional<UserView> currentUser(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        return sessions.find(sessionToken)
                .filter(s -> {
                    if (s.isActive(now)) return true;
                    sessions.delete(s.token()); // limpieza perezosa de sesiones vencidas
                    return false;
                })
                .flatMap(s -> users.findById(s.userId()))
                .map(UserView::from);
    }
}
