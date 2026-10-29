package co.rutamunicipios.application.port.in;

import java.util.Optional;

/** Puerto de entrada HU-02: Inicio de sesión real. */
public interface AuthenticationUseCase {

    record LoginCommand(String email, String password) {}

    record LoginResult(String sessionToken, long maxAgeSeconds, UserView user) {}

    LoginResult login(LoginCommand command);

    void logout(String sessionToken);

    /** Resuelve el usuario de una sesión válida; vacío si no hay sesión o expiró. */
    Optional<UserView> currentUser(String sessionToken);
}
