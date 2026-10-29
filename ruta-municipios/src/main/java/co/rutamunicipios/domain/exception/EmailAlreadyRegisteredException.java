package co.rutamunicipios.domain.exception;

public final class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException() {
        super("CORREO_DUPLICADO", "Ya existe una cuenta con este correo.");
    }
}
