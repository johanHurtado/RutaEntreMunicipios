package co.rutamunicipios.infrastructure;

import co.rutamunicipios.infrastructure.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    @DisplayName("Ida y vuelta con tildes, comillas, números y anidación")
    void roundTrip() {
        Map<String, Object> m = Json.parseObject("{\"n\":\"Tuluá \\\"V\\\"\",\"x\":3.5,\"i\":7,\"ok\":true,\"l\":[1,null]}");
        assertEquals("Tuluá \"V\"", m.get("n"));
        assertEquals(3.5, m.get("x"));
        assertEquals(7L, m.get("i"));
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertEquals(2, ((List<?>) m.get("l")).size());
        assertEquals(m, Json.parseObject(Json.write(m)));
    }

    @Test
    @DisplayName("JSON mal formado lanza IllegalArgumentException")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
    }
}
