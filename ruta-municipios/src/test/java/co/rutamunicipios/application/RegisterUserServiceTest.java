package co.rutamunicipios.application;

import co.rutamunicipios.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import co.rutamunicipios.application.port.in.UserView;
import co.rutamunicipios.application.service.RegisterUserService;
import co.rutamunicipios.domain.exception.EmailAlreadyRegisteredException;
import co.rutamunicipios.domain.exception.InvalidEmailException;
import co.rutamunicipios.domain.exception.WeakPasswordException;
import co.rutamunicipios.domain.model.User;
import co.rutamunicipios.support.FastHasher;
import co.rutamunicipios.support.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class RegisterUserServiceTest {

    private InMemoryUserRepository repo;
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryUserRepository();
        service = new RegisterUserService(repo, FastHasher.create(), Clock.systemUTC());
    }

    @Test
    @DisplayName("HU-01 éxito: registra y guarda la contraseña protegida (nunca en texto plano)")
    void registersWithHashedPassword() {
        UserView view = service.register(new RegisterUserCommand("Johan Hurtado", "johan@correo.com", "Segura2026"));

        assertEquals("johan@correo.com", view.email());
        User stored = repo.data.get(view.id());
        assertNotNull(stored);
        assertNotEquals("Segura2026", stored.passwordHash());
        assertTrue(stored.passwordHash().startsWith("pbkdf2_sha256$"));
        assertFalse(stored.passwordHash().contains("Segura2026"));
    }

    @Test
    @DisplayName("HU-01 inválido: correo con formato incorrecto")
    void invalidEmail() {
        assertThrows(InvalidEmailException.class,
                () -> service.register(new RegisterUserCommand("Diego", "diego@", "Segura2026")));
        assertTrue(repo.data.isEmpty());
    }

    @Test
    @DisplayName("HU-01 límite: contraseña de 7 caracteres")
    void shortPassword() {
        assertThrows(WeakPasswordException.class,
                () -> service.register(new RegisterUserCommand("Santiago", "santi@correo.com", "1234567")));
    }

    @Test
    @DisplayName("HU-01 duplicado: mismo correo aunque cambien mayúsculas")
    void duplicateEmail() {
        service.register(new RegisterUserCommand("Alejandro", "ale@correo.com", "Segura2026"));
        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.register(new RegisterUserCommand("Otro", "ALE@correo.com", "OtraClave99")));
        assertEquals(1, repo.data.size());
    }
}
