package co.rutamunicipios.infrastructure.adapter.in.web;

import co.rutamunicipios.application.port.in.AuthenticationUseCase;
import co.rutamunicipios.application.port.in.AuthenticationUseCase.LoginCommand;
import co.rutamunicipios.application.port.in.AuthenticationUseCase.LoginResult;
import co.rutamunicipios.application.port.in.OriginUseCase;
import co.rutamunicipios.application.port.in.PasswordRecoveryUseCase;
import co.rutamunicipios.application.port.in.RegisterUserUseCase;
import co.rutamunicipios.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import co.rutamunicipios.application.port.in.UserView;
import co.rutamunicipios.domain.exception.DomainException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static co.rutamunicipios.infrastructure.adapter.in.web.HttpSupport.*;

/**
 * Adaptador de entrada REST (/api/*). Convierte HTTP ⇄ comandos de los casos de uso.
 * No contiene reglas de negocio: solo traduce y delega.
 */
public final class ApiHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(ApiHandler.class.getName());

    private final RegisterUserUseCase register;
    private final AuthenticationUseCase auth;
    private final PasswordRecoveryUseCase recovery;
    private final OriginUseCase origin;
    private final boolean secureCookies;
    private final boolean devMailbox;
    private final MapSettings mapSettings;

    /** Datos públicos del mapa que necesita el navegador (la clave de Maps JS es visible por diseño de Google). */
    public record MapSettings(String googleMapsApiKey, String googleMapId) {
        public static MapSettings none() {
            return new MapSettings("", "");
        }

        boolean google() {
            return googleMapsApiKey != null && !googleMapsApiKey.isBlank();
        }
    }

    public ApiHandler(RegisterUserUseCase register, AuthenticationUseCase auth,
                      PasswordRecoveryUseCase recovery, OriginUseCase origin,
                      boolean secureCookies, boolean devMailbox, MapSettings mapSettings) {
        this.register = register;
        this.auth = auth;
        this.recovery = recovery;
        this.origin = origin;
        this.secureCookies = secureCookies;
        this.devMailbox = devMailbox;
        this.mapSettings = mapSettings;
        HttpSupport.enableGoogleMaps(mapSettings.google());
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String route = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
        try {
            switch (route) {
                case "POST /api/registro" -> register(ex);
                case "POST /api/login" -> login(ex);
                case "POST /api/logout" -> logout(ex);
                case "GET /api/me" -> me(ex);
                case "GET /api/config" -> config(ex);
                case "POST /api/recuperar" -> requestReset(ex);
                case "GET /api/restablecer/validar" -> validateToken(ex);
                case "POST /api/restablecer" -> resetPassword(ex);
                case "POST /api/origen/gps" -> gpsOrigin(ex);
                case "POST /api/origen/manual" -> manualOrigin(ex);
                default -> sendJson(ex, 404, Map.of("error", "NO_ENCONTRADO", "message", "Ruta no existe"));
            }
        } catch (DomainException e) {
            ErrorMapper.HttpError err = ErrorMapper.map(e);
            sendJson(ex, err.status(), err.body());
        } catch (BadRequest e) {
            sendJson(ex, 400, Map.of("error", "SOLICITUD_INVALIDA", "message", e.getMessage()));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error no controlado en " + route, e);
            sendJson(ex, 500, Map.of("error", "ERROR_INTERNO", "message", "Ocurrió un error. Intenta de nuevo."));
        }
    }

    // HU-01 ---------------------------------------------------------------
    private void register(HttpExchange ex) throws IOException {
        Map<String, Object> b = readJson(ex);
        UserView user = register.register(new RegisterUserCommand(str(b, "name"), str(b, "email"), str(b, "password")));
        sendJson(ex, 201, Map.of("user", userJson(user)));
    }

    // HU-02 ---------------------------------------------------------------
    private void login(HttpExchange ex) throws IOException {
        Map<String, Object> b = readJson(ex);
        LoginResult r = auth.login(new LoginCommand(str(b, "email"), str(b, "password")));
        setSessionCookie(ex, r.sessionToken(), r.maxAgeSeconds(), secureCookies);
        sendJson(ex, 200, Map.of("user", userJson(r.user()), "redirect", "/inicio"));
    }

    private void logout(HttpExchange ex) throws IOException {
        sessionToken(ex).ifPresent(auth::logout);
        clearSessionCookie(ex, secureCookies);
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void me(HttpExchange ex) throws IOException {
        Optional<UserView> user = sessionToken(ex).flatMap(auth::currentUser);
        if (user.isEmpty()) {
            sendJson(ex, 401, Map.of("error", "SIN_SESION", "message", "Inicia sesión para continuar."));
            return;
        }
        sendJson(ex, 200, Map.of("user", userJson(user.get())));
    }

    /** HU-03: qué proveedor de mapa usar. Solo para usuarios con sesión. */
    private void config(HttpExchange ex) throws IOException {
        requireUser(ex);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mapProvider", mapSettings.google() ? "google" : "leaflet");
        if (mapSettings.google()) {
            out.put("googleMapsApiKey", mapSettings.googleMapsApiKey());
            out.put("googleMapId", mapSettings.googleMapId());
        }
        sendJson(ex, 200, out);
    }

    // HU-07 ---------------------------------------------------------------
    private void requestReset(HttpExchange ex) throws IOException {
        Map<String, Object> b = readJson(ex);
        recovery.requestReset(str(b, "email"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Si el correo está registrado, te enviamos un enlace. Revisa tu bandeja en los próximos minutos.");
        if (devMailbox) out.put("devMailbox", "/dev/correos"); // solo en modo desarrollo
        sendJson(ex, 202, out);
    }

    private void validateToken(HttpExchange ex) throws IOException {
        boolean usable = recovery.isTokenUsable(query(ex).get("token"));
        sendJson(ex, 200, Map.of("usable", usable));
    }

    private void resetPassword(HttpExchange ex) throws IOException {
        Map<String, Object> b = readJson(ex);
        recovery.resetPassword(str(b, "token"), str(b, "password"));
        clearSessionCookie(ex, secureCookies);
        sendJson(ex, 200, Map.of("message", "Contraseña actualizada. Inicia sesión con la nueva."));
    }

    // HU-03 ---------------------------------------------------------------
    private void gpsOrigin(HttpExchange ex) throws IOException {
        UserView user = requireUser(ex);
        Map<String, Object> b = readJson(ex);
        OriginUseCase.GpsOriginResult r = origin.setOriginFromCoordinates(user.id(), num(b, "lat"), num(b, "lon"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resolved", r.resolved());
        out.put("municipality", r.municipality());
        out.put("user", userJson(r.user()));
        sendJson(ex, 200, out);
    }

    private void manualOrigin(HttpExchange ex) throws IOException {
        UserView user = requireUser(ex);
        Map<String, Object> b = readJson(ex);
        UserView updated = origin.setManualOrigin(user.id(), str(b, "municipality"));
        sendJson(ex, 200, Map.of("user", userJson(updated)));
    }

    private UserView requireUser(HttpExchange ex) {
        return sessionToken(ex).flatMap(auth::currentUser)
                .orElseThrow(co.rutamunicipios.domain.exception.InvalidCredentialsException::new);
    }

    private static Map<String, Object> userJson(UserView u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("name", u.name());
        m.put("email", u.email());
        if (u.origin() != null) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("municipality", u.origin().municipality());
            o.put("lat", u.origin().latitude());
            o.put("lon", u.origin().longitude());
            o.put("source", u.origin().source());
            m.put("origin", o);
        } else {
            m.put("origin", null);
        }
        return m;
    }
}
