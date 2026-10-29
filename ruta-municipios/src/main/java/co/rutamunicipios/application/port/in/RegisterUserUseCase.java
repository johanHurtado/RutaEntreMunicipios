package co.rutamunicipios.application.port.in;

/** Puerto de entrada HU-01: Registro persistente. */
public interface RegisterUserUseCase {

    record RegisterUserCommand(String name, String email, String password) {}

    UserView register(RegisterUserCommand command);
}
