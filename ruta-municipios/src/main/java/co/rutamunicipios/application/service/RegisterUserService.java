package co.rutamunicipios.application.service;

import co.rutamunicipios.application.port.in.RegisterUserUseCase;
import co.rutamunicipios.application.port.in.UserView;
import co.rutamunicipios.application.port.out.PasswordHasher;
import co.rutamunicipios.application.port.out.UserRepository;
import co.rutamunicipios.domain.exception.EmailAlreadyRegisteredException;
import co.rutamunicipios.domain.model.Email;
import co.rutamunicipios.domain.model.PasswordPolicy;
import co.rutamunicipios.domain.model.User;

import java.time.Clock;

/**
 * Caso de uso HU-01. Orquesta: validar → verificar duplicado → proteger contraseña → persistir.
 * No conoce HTTP, ni archivos, ni algoritmos de hash: solo puertos.
 */
public final class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegisterUserService(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    public UserView register(RegisterUserCommand command) {
        Email email = Email.of(command.email());          // formato de correo
        PasswordPolicy.validate(command.password());      // mínimo 8 caracteres

        // synchronized sobre el repositorio: evita que dos registros simultáneos
        // con el mismo correo pasen ambos la verificación de duplicado.
        synchronized (users) {
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyRegisteredException();
            }
            User user = User.register(command.name(), email, hasher.hash(command.password()), clock.instant());
            users.save(user);
            return UserView.from(user);
        }
    }
}
