package co.rutamunicipios.domain.model;

import co.rutamunicipios.domain.exception.InvalidOriginException;

import java.time.Instant;

/**
 * Municipio de origen del viaje (HU-03).
 * Puede venir del GPS (con coordenadas) o escrito a mano cuando se niega el permiso.
 */
public record Origin(String municipality, Double latitude, Double longitude, Source source, Instant updatedAt) {

    public enum Source { GPS, MANUAL }

    public Origin {
        if (municipality == null || municipality.isBlank()) {
            throw new InvalidOriginException("Escribe el municipio de origen.");
        }
        municipality = municipality.trim().replaceAll("\\s+", " ");
        if (municipality.length() < 3 || municipality.length() > 120) {
            throw new InvalidOriginException("El municipio debe tener entre 3 y 120 caracteres.");
        }
        if (source == Source.GPS) {
            validateCoordinates(latitude, longitude);
        }
    }

    public static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new InvalidOriginException("Coordenadas fuera de rango.");
        }
    }

    public static Origin fromGps(String municipality, double lat, double lon, Instant now) {
        return new Origin(municipality, lat, lon, Source.GPS, now);
    }

    public static Origin manual(String municipality, Instant now) {
        return new Origin(municipality, null, null, Source.MANUAL, now);
    }
}
