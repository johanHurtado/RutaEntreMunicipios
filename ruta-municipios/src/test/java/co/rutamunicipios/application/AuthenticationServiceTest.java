package co.rutamunicipios.application;

import co.rutamunicipios.application.port.in.AuthenticationUseCase.LoginCommand;
import co.rutamunicipios.application.port.in.AuthenticationUseCase.LoginResult;
import co.rutamunicipios.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import co.rutamunicipios.application.service.AuthenticationService;
import co.rutamunicipios.application.service.RegisterUserService;
import co.rutamunicipios.domain.exception.InvalidCredentialsException;
import co.rutamunicipios.infrastructure.adapter.out.persistence.InMemorySessionRepository;
import co.rutamunicipios.infrastructure.adapter.out.security.SecureTokenGenerator;
import co.rutamunicipios.support.FastHasher;
import co.rutamunicipios.support.InMemoryUserRepository;
import co.rutamunicipios.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {

    private MutableClock clock;
    private AuthenticationService auth;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-21T12:00:00Z"));
        var users = new InMemoryUserRepository();
        var hasher = FastHasher.create();
        new RegisterUserService(users, hasher, clock)
                .register(new RegisterUserCommand("Johan", "johan@correo.com", "Segura2026"));
        auth = new AuthenticationService(users, hasher, new InMemorySessionRepository(),
                new SecureTokenGenerator(), clock, Duration.ofMinutes(120));
    }

    @Test
    @DisplayName("HU-02 éxito: cuenta recién creada inicia sesión y obtiene sesión válida")
    void validLogin() {
        LoginResult r = auth.login(new LoginCommand("johan@correo.com", "Segura2026"));
        assertNotNull(r.sessionToken());
        assertEquals("Johan", auth.currentUser(r.sessionToken()).orElseThrow().name());
    }

    @Test
    @DisplayName("HU-02 inválido: contraseña errada y correo inexistente dan el MISMO error genérico")
    void genericError() {
        var wrongPass = assertThrows(InvalidCredentialsException.class,
                () -> auth.login(new LoginCommand("johan@correo.com", "Incorrecta1")));
        var noUser = assertThrows(InvalidCredentialsException.class,
                () -> auth.login(new LoginCommand("nadie@correo.com", "Segura2026")));
        assertEquals(wrongPass.getMessage(), noUser.getMessage());
    }

    @Test
    @DisplayName("HU-02: sin sesión, sesión inventada o vencida no dan acceso")
    void noSession() {
        assertTrue(auth.currentUser(null).isEmpty());
        assertTrue(auth.currentUser("token-inventado").isEmpty());

        LoginResult r = auth.login(new LoginCommand("johan@correo.com", "Segura2026"));
        clock.advance(Duration.ofMinutes(121));
        assertTrue(auth.currentUser(r.sessionToken()).isEmpty());
    }

    @Test
    @DisplayName("HU-02: cerrar sesión invalida el token")
    void logout() {
        LoginResult r = auth.login(new LoginCommand("johan@correo.com", "Segura2026"));
        auth.logout(r.sessionToken());
        assertTrue(auth.currentUser(r.sessionToken()).isEmpty());
    }
}
