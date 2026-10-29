package co.rutamunicipios.domain.exception;

public final class InvalidOriginException extends DomainException {
    public InvalidOriginException(String message) {
        super("ORIGEN_INVALIDO", message);
    }
}
