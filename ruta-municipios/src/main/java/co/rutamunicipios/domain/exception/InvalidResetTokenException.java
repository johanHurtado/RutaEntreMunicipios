package co.rutamunicipios.domain.exception;

public final class InvalidResetTokenException extends DomainException {
    public InvalidResetTokenException() {
        super("ENLACE_INVALIDO", "El enlace de recuperación no es válido o ya venció.");
    }
}
