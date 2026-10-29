package co.rutamunicipios.domain.exception;

public final class InvalidEmailException extends DomainException {
    public InvalidEmailException() {
        super("EMAIL_INVALIDO", "El correo no tiene un formato válido.");
    }
}
