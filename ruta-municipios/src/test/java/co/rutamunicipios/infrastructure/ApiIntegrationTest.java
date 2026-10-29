package co.rutamunicipios.infrastructure;

import co.rutamunicipios.infrastructure.config.AppConfig;
import co.rutamunicipios.infrastructure.config.Application;
import co.rutamunicipios.infrastructure.json.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración del paquete completo (sección 5 del documento):
 * crear cuenta → iniciar sesión → origen → recuperar contraseña → entrar con la nueva.
 * Levanta el servidor real en un puerto libre con una carpeta de datos temporal.
 */
class ApiIntegrationTest {

    private Application app;
    private Path dataDir;
    private String base;
    private HttpClient client;

    @BeforeEach
    void start() throws Exception {
        dataDir = Files.createTempDirectory("rem-it");
        AppConfig cfg = new AppConfig(0, "http://localhost", dataDir, Duration.ofMinutes(120),
                Duration.ofMinutes(30), false, false, "http://127.0.0.1:9", "", 465, "", "", "", "", "", "");
        app = new Application(cfg);
        app.start();
        base = "http://127.0.0.1:" + app.port(); // CookieManager de Java asigna dominio "localhost.local"
        client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    @DisplayName("Integración HU-01/02/03/07 con el mismo backend y la misma cuenta")
    void fullJourney() throws Exception {
        // Sin sesión: /inicio redirige a /login y /api/me responde 401
        assertEquals(302, get("/inicio").statusCode());
        assertEquals("/login?motivo=sesion", get("/inicio").headers().firstValue("Location").orElse(""));
        assertEquals(401, get("/api/me").statusCode());
        assertEquals(401, get("/api/config").statusCode(), "la configuración del mapa exige sesión");

        // HU-01 registro: positivo, inválido y duplicado
        assertEquals(201, post("/api/registro", Map.of("name", "Alejandro Torres",
                "email", "ale@correo.com", "password", "Segura2026")).statusCode());
        assertEquals(422, post("/api/registro", Map.of("name", "X Y", "email", "malo", "password", "Segura2026")).statusCode());
        assertEquals(409, post("/api/registro", Map.of("name", "Otro", "email", "ALE@correo.com",
                "password", "Segura2026")).statusCode());
        assertFalse(Files.readString(dataDir.resolve("users.json")).contains("Segura2026"));

        // HU-02 login inválido (genérico) y válido
        var bad = post("/api/login", Map.of("email", "ale@correo.com", "password", "Incorrecta"));
        assertEquals(401, bad.statusCode());
        assertTrue(bad.body().contains("Correo o contraseña incorrectos"));
        assertEquals(200, post("/api/login", Map.of("email", "ale@correo.com", "password", "Segura2026")).statusCode());
        assertEquals(200, get("/inicio").statusCode());

        // HU-03 origen (GPS: el geocodificador remoto no responde → respaldo local) y manual
        var gps = post("/api/origen/gps", Map.of("lat", 3.4516, "lon", -76.532));
        assertEquals(200, gps.statusCode());
        assertTrue(gps.body().contains("Cali"));
        assertEquals(200, post("/api/origen/manual", Map.of("municipality", "Palmira")).statusCode());
        assertTrue(get("/api/me").body().contains("Palmira"));

        // Sin clave de Google configurada, el frontend usa Leaflet + OpenStreetMap
        assertTrue(get("/api/config").body().contains("\"mapProvider\":\"leaflet\""));

        // HU-07 recuperación: el correo llega al buzón de prueba con el enlace
        assertEquals(202, post("/api/recuperar", Map.of("email", "ale@correo.com")).statusCode());
        String mailbox = get("/dev/correos").body();
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mailbox);
        assertTrue(m.find(), "el buzón debe mostrar el enlace");
        String token = m.group(1);

        assertTrue(get("/api/restablecer/validar?token=" + token).body().contains("true"));
        assertEquals(200, post("/api/restablecer", Map.of("token", token, "password", "Nueva2026")).statusCode());
        assertEquals(410, post("/api/restablecer", Map.of("token", token, "password", "Otra20266")).statusCode());

        // Cambiar la contraseña cerró la sesión; la vieja ya no sirve, la nueva sí
        assertEquals(302, get("/inicio").statusCode());
        assertEquals(401, post("/api/login", Map.of("email", "ale@correo.com", "password", "Segura2026")).statusCode());
        assertEquals(200, post("/api/login", Map.of("email", "ale@correo.com", "password", "Nueva2026")).statusCode());
        assertTrue(get("/api/me").body().contains("Palmira"), "misma cuenta, mismo origen guardado");
    }

    @Test
    @DisplayName("Seguridad: POST sin JSON se rechaza (defensa CSRF) y path traversal da 404")
    void hardening() throws Exception {
        var form = client.send(HttpRequest.newBuilder(URI.create(base + "/api/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("email=a&password=b")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, form.statusCode());
        assertEquals(404, get("/css/../../secret").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
