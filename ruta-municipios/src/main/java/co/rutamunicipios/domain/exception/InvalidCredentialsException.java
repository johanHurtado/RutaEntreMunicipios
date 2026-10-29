package co.rutamunicipios.domain.exception;

/** Mensaje deliberadamente genérico: no revela si el correo existe (HU-02). */
public final class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos.");
    }
}
