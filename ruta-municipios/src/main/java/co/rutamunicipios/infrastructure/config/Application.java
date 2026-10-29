
    package co.rutamunicipios.infrastructure.config;

import co.rutamunicipios.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import co.rutamunicipios.application.port.out.EmailSender;
import co.rutamunicipios.application.port.out.PasswordHasher;
import co.rutamunicipios.application.port.out.ResetTokenRepository;
import co.rutamunicipios.application.port.out.ReverseGeocoder;
import co.rutamunicipios.application.port.out.SessionRepository;
import co.rutamunicipios.application.port.out.TokenGenerator;
import co.rutamunicipios.application.port.out.UserRepository;
import co.rutamunicipios.application.service.AuthenticationService;
import co.rutamunicipios.application.service.OriginService;
import co.rutamunicipios.application.service.PasswordRecoveryService;
import co.rutamunicipios.application.service.RegisterUserService;
import co.rutamunicipios.domain.model.Email;
import co.rutamunicipios.infrastructure.adapter.in.web.ApiHandler;
import co.rutamunicipios.infrastructure.adapter.in.web.DevMailboxHandler;
import co.rutamunicipios.infrastructure.adapter.in.web.PageHandler;
import co.rutamunicipios.infrastructure.adapter.in.web.WebServer;
import co.rutamunicipios.infrastructure.adapter.out.geocoding.FallbackReverseGeocoder;
import co.rutamunicipios.infrastructure.adapter.out.geocoding.GoogleReverseGeocoder;
import co.rutamunicipios.infrastructure.adapter.out.geocoding.NearestKnownMunicipalityGeocoder;
import co.rutamunicipios.infrastructure.adapter.out.geocoding.NominatimReverseGeocoder;
import co.rutamunicipios.infrastructure.adapter.out.mail.OutboxEmailSender;
import co.rutamunicipios.infrastructure.adapter.out.mail.SmtpEmailSender;
import co.rutamunicipios.infrastructure.adapter.out.persistence.InMemorySessionRepository;
import co.rutamunicipios.infrastructure.adapter.out.persistence.JsonFileResetTokenRepository;
import co.rutamunicipios.infrastructure.adapter.out.persistence.JsonFileUserRepository;
import co.rutamunicipios.infrastructure.adapter.out.security.Pbkdf2PasswordHasher;
import co.rutamunicipios.infrastructure.adapter.out.security.SecureTokenGenerator;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Raíz de composición (Composition Root): el ÚNICO lugar que conoce todas las clases concretas.
 * Aquí se "enchufan" los adaptadores a los puertos (inyección de dependencias manual).
 */
public final class Application {

    private static final Logger LOG = Logger.getLogger(Application.class.getName());

    public static final String DEMO_EMAIL = "demo@rutamunicipios.co";
    public static final String DEMO_PASSWORD = "Demo2026!";

    private final WebServer server;

    public Application(AppConfig cfg) throws IOException {
        Clock clock = Clock.systemUTC();

        // ---- Adaptadores de salida
        UserRepository users = new JsonFileUserRepository(cfg.dataDir().resolve("users.json"));
        ResetTokenRepository resetTokens = new JsonFileResetTokenRepository(cfg.dataDir().resolve("reset-tokens.json"));
        SessionRepository sessions = new InMemorySessionRepository();
        PasswordHasher hasher = new Pbkdf2PasswordHasher();
        TokenGenerator tokens = new SecureTokenGenerator();
        OutboxEmailSender outbox = cfg.smtpConfigured() ? null : new OutboxEmailSender(cfg.dataDir().resolve("outbox"));
        EmailSender mail = cfg.smtpConfigured()
                ? new SmtpEmailSender(new SmtpEmailSender.SmtpSettings(cfg.smtpHost(), cfg.smtpPort(),
                        cfg.smtpUser(), cfg.smtpPassword(), cfg.smtpFrom()))
                : outbox;
        // Cadena de geocodificadores: Google (si hay clave) → Nominatim → respaldo sin internet
        List<ReverseGeocoder> chain = new ArrayList<>();
        if (!cfg.googleGeocodingApiKey().isBlank()) chain.add(new GoogleReverseGeocoder(cfg.googleGeocodingApiKey()));
        chain.add(new NominatimReverseGeocoder(cfg.nominatimUrl()));
        chain.add(new NearestKnownMunicipalityGeocoder());
        var geocoder = new FallbackReverseGeocoder(chain);

        // ---- Casos de uso (núcleo)
        var registerService = new RegisterUserService(users, hasher, clock);
        var authService = new AuthenticationService(users, hasher, sessions, tokens, clock, cfg.sessionTtl());
        var recoveryService = new PasswordRecoveryService(users, resetTokens, sessions, hasher, tokens, mail, clock,
                new PasswordRecoveryService.Settings(cfg.baseUrl(), cfg.resetTokenTtl()));
        var originService = new OriginService(users, geocoder, clock);

        if (cfg.seedDemoUser() && !users.existsByEmail(Email.of(DEMO_EMAIL))) {
            registerService.register(new RegisterUserCommand("Usuario Demo", DEMO_EMAIL, DEMO_PASSWORD));
            LOG.info("Usuario de prueba creado: " + DEMO_EMAIL);
        }

        // ---- Adaptadores de entrada
        var api = new ApiHandler(registerService, authService, recoveryService, originService,
                cfg.secureCookies(), outbox != null,
                new ApiHandler.MapSettings(cfg.googleMapsApiKey(), cfg.googleMapId()));
        var pages = new PageHandler(authService);
        server = new WebServer(cfg.port(), api, pages, outbox == null ? null : new DevMailboxHandler(outbox));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public int port() {
        return server.port();
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tT %4$s %5$s%6$s%n");
        AppConfig cfg = AppConfig.fromEnvironment();
        Application app = new Application(cfg);
        app.start();
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(app::stop));
        System.out.printf("""

                  +----------------------------------------------------------+
                    Ruta Entre Municipios · Java %s
                    App:            %s
                    Correos:        %s
                    Mapa:           %s
                    Token recuperación vence en %d min
                    Usuario prueba: %s / %s
                  +----------------------------------------------------------+
                %n""", Runtime.version().feature(), cfg.baseUrl(),
                cfg.smtpConfigured() ? "SMTP " + cfg.smtpHost() : cfg.baseUrl() + "/dev/correos (buzón de prueba)",
                cfg.googleMapsEnabled() ? "Google Maps" : "Leaflet + OpenStreetMap (sin clave de Google)",
                cfg.resetTokenTtl().toMinutes(), DEMO_EMAIL, DEMO_PASSWORD);
    }
}
