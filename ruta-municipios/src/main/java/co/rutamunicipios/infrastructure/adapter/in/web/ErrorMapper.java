package co.rutamunicipios.infrastructure.adapter.in.web;

import co.rutamunicipios.domain.exception.DomainException;
import co.rutamunicipios.domain.exception.EmailAlreadyRegisteredException;
import co.rutamunicipios.domain.exception.InvalidCredentialsException;
import co.rutamunicipios.domain.exception.InvalidEmailException;
import co.rutamunicipios.domain.exception.InvalidNameException;
import co.rutamunicipios.domain.exception.InvalidOriginException;
import co.rutamunicipios.domain.exception.InvalidResetTokenException;
import co.rutamunicipios.domain.exception.WeakPasswordException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce excepciones de dominio a HTTP. Como DomainException es "sealed",
 * este switch es EXHAUSTIVO: si mañana se agrega una excepción nueva y no se
 * mapea aquí, el código no compila (pattern matching for switch, Java 21).
 */
final class ErrorMapper {

    record HttpError(int status, Map<String, Object> body) {}

    private ErrorMapper() {
    }

    static HttpError map(DomainException e) {
        int status = switch (e) {
            case InvalidEmailException ignored -> 422;
            case WeakPasswordException ignored -> 422;
            case InvalidNameException ignored -> 422;
            case InvalidOriginException ignored -> 422;
            case EmailAlreadyRegisteredException ignored -> 409;
            case InvalidCredentialsException ignored -> 401;
            case InvalidResetTokenException ignored -> 410;
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.code());
        body.put("message", e.getMessage());
        body.put("field", fieldOf(e));
        return new HttpError(status, body);
    }

    private static String fieldOf(DomainException e) {
        return switch (e) {
            case InvalidEmailException ignored -> "email";
            case EmailAlreadyRegisteredException ignored -> "email";
            case WeakPasswordException ignored -> "password";
            case InvalidNameException ignored -> "name";
            case InvalidOriginException ignored -> "origin";
            case InvalidCredentialsException ignored -> null;
            case InvalidResetTokenException ignored -> "token";
        };
    }
}
