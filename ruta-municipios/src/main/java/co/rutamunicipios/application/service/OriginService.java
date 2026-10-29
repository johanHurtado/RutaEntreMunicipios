package co.rutamunicipios.application.service;

import co.rutamunicipios.application.port.in.OriginUseCase;
import co.rutamunicipios.application.port.in.UserView;
import co.rutamunicipios.application.port.out.ReverseGeocoder;
import co.rutamunicipios.application.port.out.UserRepository;
import co.rutamunicipios.domain.exception.InvalidCredentialsException;
import co.rutamunicipios.domain.model.Origin;
import co.rutamunicipios.domain.model.User;
import co.rutamunicipios.domain.model.UserId;

import java.time.Clock;
import java.util.Optional;

/** Caso de uso HU-03: fijar el municipio de origen por GPS o manualmente. */
public final class OriginService implements OriginUseCase {

    private final UserRepository users;
    private final ReverseGeocoder geocoder;
    private final Clock clock;

    public OriginService(UserRepository users, ReverseGeocoder geocoder, Clock clock) {
        this.users = users;
        this.geocoder = geocoder;
        this.clock = clock;
    }

    @Override
    public GpsOriginResult setOriginFromCoordinates(String userId, double latitude, double longitude) {
        Origin.validateCoordinates(latitude, longitude);
        User user = load(userId);
        Optional<String> municipality = geocoder.municipalityAt(latitude, longitude);
        if (municipality.isEmpty()) {
            // No se pudo identificar: el frontend pide escribirlo a mano.
            return new GpsOriginResult(null, false, UserView.from(user));
        }
        user.updateOrigin(Origin.fromGps(municipality.get(), latitude, longitude, clock.instant()));
        users.save(user);
        return new GpsOriginResult(user.origin().municipality(), true, UserView.from(user));
    }

    @Override
    public UserView setManualOrigin(String userId, String municipality) {
        User user = load(userId);
        user.updateOrigin(Origin.manual(municipality, clock.instant()));
        users.save(user);
        return UserView.from(user);
    }

    private User load(String userId) {
        return users.findById(new UserId(userId)).orElseThrow(InvalidCredentialsException::new);
    }
}
