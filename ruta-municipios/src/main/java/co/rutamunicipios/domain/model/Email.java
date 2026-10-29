package co.rutamunicipios.domain.model;

import co.rutamunicipios.domain.exception.InvalidEmailException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Value Object: un correo SIEMPRE válido y normalizado.
 * Si existe una instancia de Email, su formato ya fue verificado.
 */
public record Email(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null) throw new InvalidEmailException();
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException();
        }
    }

    public static Email of(String raw) {
        return new Email(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
