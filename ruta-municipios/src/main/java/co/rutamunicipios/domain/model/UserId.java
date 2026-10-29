package co.rutamunicipios.domain.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(String value) {
    public UserId {
        Objects.requireNonNull(value, "UserId requerido");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }
}
