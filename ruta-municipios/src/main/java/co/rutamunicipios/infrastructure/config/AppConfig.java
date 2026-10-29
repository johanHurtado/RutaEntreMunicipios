package co.rutamunicipios.infrastructure.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Configuración leída (en este orden) de -Dpropiedades, variables de entorno o el archivo
 * config.properties de la carpeta del proyecto. Todo tiene valor por defecto, así la app
 * arranca sin modificar código (requisito de la sustentación).
 */
public record AppConfig(
        int port,
        String baseUrl,
        Path dataDir,
        Duration sessionTtl,
        Duration resetTokenTtl,
        boolean secureCookies,
        boolean seedDemoUser,
        String nominatimUrl,
        String smtpHost,
        int smtpPort,
        String smtpUser,
        String smtpPassword,
        String smtpFrom,
        String googleMapsApiKey,
        String googleMapId,
        String googleGeocodingApiKey) {

    private static final Properties FILE = loadFile();

    public static AppConfig fromEnvironment() {
        int port = Integer.parseInt(get("PORT", "8080"));
        return new AppConfig(
                port,
                stripSlash(get("APP_BASE_URL", "http://localhost:" + port)),
                Path.of(get("DATA_DIR", "data")),
                Duration.ofMinutes(Long.parseLong(get("SESSION_TTL_MINUTES", "120"))),
                Duration.ofMinutes(Long.parseLong(get("RESET_TOKEN_TTL_MINUTES", "30"))),
                Boolean.parseBoolean(get("SECURE_COOKIES", "false")),
                Boolean.parseBoolean(get("SEED_DEMO_USER", "true")),
                stripSlash(get("NOMINATIM_URL", "https://nominatim.openstreetmap.org")),
                get("SMTP_HOST", ""),
                Integer.parseInt(get("SMTP_PORT", "465")),
                get("SMTP_USER", ""),
                get("SMTP_PASSWORD", ""),
                get("SMTP_FROM", get("SMTP_USER", "")),
                get("GOOGLE_MAPS_API_KEY", ""),
                get("GOOGLE_MAP_ID", "DEMO_MAP_ID"),
                get("GOOGLE_GEOCODING_API_KEY", get("GOOGLE_MAPS_API_KEY", "")));
    }

    /** Si hay clave de Google Maps, el mapa usa Google; si no, Leaflet + OpenStreetMap. */
    public boolean googleMapsEnabled() {
        return !googleMapsApiKey.isBlank();
    }

    public boolean smtpConfigured() {
        return !smtpHost.isBlank() && !smtpUser.isBlank() && !smtpPassword.isBlank();
    }

    private static String get(String key, String fallback) {
        String v = System.getProperty(key, System.getenv(key));
        if (v == null || v.isBlank()) v = FILE.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static Properties loadFile() {
        Properties p = new Properties();
        Path file = Path.of(System.getProperty("CONFIG_FILE", "config.properties"));
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                System.err.println("No se pudo leer " + file + ": " + e.getMessage());
            }
        }
        return p;
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
