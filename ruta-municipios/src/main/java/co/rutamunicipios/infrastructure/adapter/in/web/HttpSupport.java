package co.rutamunicipios.infrastructure.adapter.in.web;

import co.rutamunicipios.infrastructure.json.Json;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Utilidades HTTP compartidas por los controladores (adaptadores de entrada). */
final class HttpSupport {

    static final String SESSION_COOKIE = "REM_SESSION";
    private static final int MAX_BODY_BYTES = 16 * 1024;

    private static final String CSP_BASE = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "font-src 'self'",
            "img-src 'self' data: https://*.tile.openstreetmap.org",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    /** Política recomendada por Google para la Maps JavaScript API (lista de dominios permitidos). */
    private static final String CSP_GOOGLE = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-eval' blob: https://*.googleapis.com https://*.gstatic.com https://*.google.com"
                    + " https://*.ggpht.com https://*.googleusercontent.com",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: https://*.tile.openstreetmap.org https://*.googleapis.com https://*.gstatic.com"
                    + " https://*.google.com https://*.googleusercontent.com",
            "connect-src 'self' https://*.googleapis.com https://*.google.com https://*.gstatic.com data: blob:",
            "frame-src https://*.google.com",
            "worker-src blob:",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    private static volatile String csp = CSP_BASE;

    /** Se llama al arrancar si hay clave de Google Maps: abre la CSP solo para los dominios de Google. */
    static void enableGoogleMaps(boolean enabled) {
        csp = enabled ? CSP_GOOGLE : CSP_BASE;
    }

    private HttpSupport() {
    }

    static void securityHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        // A otros dominios solo viaja el ORIGEN (http://localhost:8080), nunca la ruta ni el ?token= de
        // /restablecer. Google Maps necesita ese origen para validar la restricción de la clave.
        h.set("Referrer-Policy", "strict-origin-when-cross-origin");
        h.set("Content-Security-Policy", csp);
        h.set("Permissions-Policy", "geolocation=(self)");
    }

    static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendBytes(HttpExchange ex, int status, String contentType, byte[] bytes, boolean cacheable)
            throws IOException {
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", cacheable ? "public, max-age=300" : "no-store");
        if ("HEAD".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void redirect(HttpExchange ex, String location) throws IOException {
        securityHeaders(ex);
        ex.getResponseHeaders().set("Location", location);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    static Map<String, Object> readJson(HttpExchange ex) throws IOException {
        String type = Optional.ofNullable(ex.getRequestHeaders().getFirst("Content-Type")).orElse("");
        // Exigir JSON evita que un formulario de otro sitio dispare acciones (defensa CSRF).
        if (!type.toLowerCase().startsWith("application/json")) {
            throw new BadRequest("Se esperaba Content-Type application/json");
        }
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
            if (data.length > MAX_BODY_BYTES) throw new BadRequest("Solicitud demasiado grande");
            return Json.parseObject(new String(data, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new BadRequest("JSON inválido");
        }
    }

    static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof String s ? s : null;
    }

    static double num(Map<String, Object> body, String key) {
        if (body.get(key) instanceof Number n) return n.doubleValue();
        throw new BadRequest("Campo numérico requerido: " + key);
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i <= 0) continue;
            out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    static Optional<String> sessionToken(HttpExchange ex) {
        var cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies == null) return Optional.empty();
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith(SESSION_COOKIE + "=")) {
                    String value = p.substring(SESSION_COOKIE.length() + 1);
                    // Algunos clientes (p. ej. CookieManager de Java) envían el valor entre comillas
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    static void setSessionCookie(HttpExchange ex, String token, long maxAge, boolean secure) {
        ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + token
                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge + (secure ? "; Secure" : ""));
    }

    static void clearSessionCookie(HttpExchange ex, boolean secure) {
        ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE
                + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (secure ? "; Secure" : ""));
    }

    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Error de forma de la solicitud (no de negocio) → HTTP 400. */
    static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }
}
