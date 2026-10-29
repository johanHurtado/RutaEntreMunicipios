package co.rutamunicipios.infrastructure;

import co.rutamunicipios.infrastructure.adapter.out.geocoding.NearestKnownMunicipalityGeocoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NearestKnownMunicipalityGeocoderTest {

    private final NearestKnownMunicipalityGeocoder geo = new NearestKnownMunicipalityGeocoder();

    @Test
    @DisplayName("Respaldo sin internet: coordenadas cercanas a Tuluá devuelven Tuluá")
    void nearTulua() {
        assertEquals("Tuluá, Valle del Cauca", geo.municipalityAt(4.09, -76.20).orElseThrow());
    }

    @Test
    @DisplayName("En medio del océano no inventa un municipio")
    void ocean() {
        assertTrue(geo.municipalityAt(0.0, -90.0).isEmpty());
    }
}
