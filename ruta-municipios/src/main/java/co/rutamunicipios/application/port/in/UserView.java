package co.rutamunicipios.application.port.in;

import co.rutamunicipios.domain.model.Origin;
import co.rutamunicipios.domain.model.User;

/** DTO de salida: lo que se puede mostrar del usuario. Nunca incluye el hash. */
public record UserView(String id, String name, String email, OriginView origin) {

    public record OriginView(String municipality, Double latitude, Double longitude, String source) {
        static OriginView from(Origin o) {
            return o == null ? null : new OriginView(o.municipality(), o.latitude(), o.longitude(), o.source().name());
        }
    }

    public static UserView from(User u) {
        return new UserView(u.id().value(), u.name(), u.email().value(), OriginView.from(u.origin()));
    }
}
