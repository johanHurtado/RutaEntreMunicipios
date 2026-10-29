package co.rutamunicipios.domain;

import co.rutamunicipios.domain.exception.InvalidOriginException;
import co.rutamunicipios.domain.model.Origin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OriginTest {

    @Test
    @DisplayName("HU-03: origen manual vacío o muy corto se rechaza")
    void manualValidation() {
        assertThrows(InvalidOriginException.class, () -> Origin.manual("  ", Instant.now()));
        assertThrows(InvalidOriginException.class, () -> Origin.manual("Ca", Instant.now()));
        assertEquals("Tuluá", Origin.manual("  Tuluá ", Instant.now()).municipality());
    }

    @Test
    @DisplayName("HU-03: coordenadas GPS fuera de rango se rechazan")
    void gpsRange() {
        assertThrows(InvalidOriginException.class, () -> Origin.fromGps("Cali", 95, -76, Instant.now()));
        assertEquals(Origin.Source.GPS, Origin.fromGps("Cali", 3.45, -76.53, Instant.now()).source());
    }
}
