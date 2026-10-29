package co.rutamunicipios.application;

import co.rutamunicipios.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import co.rutamunicipios.application.service.OriginService;
import co.rutamunicipios.application.service.RegisterUserService;
import co.rutamunicipios.domain.exception.InvalidOriginException;
import co.rutamunicipios.domain.model.Origin;
import co.rutamunicipios.support.FastHasher;
import co.rutamunicipios.support.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OriginServiceTest {

    private InMemoryUserRepository users;
    private String userId;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        userId = new RegisterUserService(users, FastHasher.create(), Clock.systemUTC())
                .register(new RegisterUserCommand("Santiago", "santi@correo.com", "Segura2026")).id();
    }

    @Test
    @DisplayName("HU-03 permiso concedido: coordenadas → municipio de origen guardado")
    void gpsResolved() {
        var service = new OriginService(users, (lat, lon) -> Optional.of("Tuluá, Valle del Cauca"), Clock.systemUTC());
        var r = service.setOriginFromCoordinates(userId, 4.0847, -76.1954);
        assertTrue(r.resolved());
        assertEquals("Tuluá, Valle del Cauca", r.municipality());
        assertEquals(Origin.Source.GPS, users.data.get(userId).origin().source());
    }

    @Test
    @DisplayName("HU-03: si no se identifica el municipio, se pide escribirlo")
    void gpsUnresolved() {
        var service = new OriginService(users, (lat, lon) -> Optional.empty(), Clock.systemUTC());
        assertFalse(service.setOriginFromCoordinates(userId, 0.0, 0.0).resolved());
        assertNull(users.data.get(userId).origin());
    }

    @Test
    @DisplayName("HU-03 permiso rechazado: origen escrito a mano; vacío se rechaza")
    void manual() {
        var service = new OriginService(users, (lat, lon) -> Optional.empty(), Clock.systemUTC());
        assertEquals("Palmira", service.setManualOrigin(userId, "Palmira").origin().municipality());
        assertThrows(InvalidOriginException.class, () -> service.setManualOrigin(userId, ""));
    }
}
