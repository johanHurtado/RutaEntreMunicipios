package co.rutamunicipios.domain.exception;

public final class WeakPasswordException extends DomainException {
    public WeakPasswordException(String message) {
        super("CONTRASENA_DEBIL", message);
    }
}
