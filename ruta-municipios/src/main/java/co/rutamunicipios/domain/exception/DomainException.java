package co.rutamunicipios.domain.exception;

/**
 * Raíz sellada (Java 17+) de todas las reglas de negocio violadas.
 * Al ser "sealed", el compilador conoce TODAS las subclases y el adaptador web
 * puede traducirlas a códigos HTTP con un switch exhaustivo (pattern matching, Java 21).
 */
public abstract sealed class DomainException extends RuntimeException
        permits InvalidEmailException,
                WeakPasswordException,
                InvalidNameException,
                EmailAlreadyRegisteredException,
                InvalidCredentialsException,
                InvalidResetTokenException,
                InvalidOriginException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Código estable que el frontend usa para decidir qué mensaje mostrar. */
    public String code() {
        return code;
    }
}
