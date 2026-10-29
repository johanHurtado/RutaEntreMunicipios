package co.rutamunicipios.domain.exception;

public final class InvalidNameException extends DomainException {
    public InvalidNameException() {
        super("NOMBRE_INVALIDO", "Escribe tu nombre (entre 2 y 80 caracteres).");
    }
}
