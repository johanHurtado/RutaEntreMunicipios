package co.rutamunicipios.infrastructure.adapter.in.web;

import co.rutamunicipios.application.port.in.AuthenticationUseCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static co.rutamunicipios.infrastructure.adapter.in.web.HttpSupport.*;

/**
 * Sirve las pantallas y los archivos estáticos desde el classpath (src/main/resources/static).
 * Protege /inicio en el SERVIDOR: sin sesión válida responde 302 a /login (HU-02).
 */
public final class PageHandler implements HttpHandler {

    private static final Map<String, String> PUBLIC_PAGES = Map.of(
            "/login", "login.html",
            "/registro", "registro.html",
            "/recuperar", "recuperar.html",
            "/restablecer", "restablecer.html");

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon",
            "woff2", "font/woff2",
            "txt", "text/plain; charset=utf-8");

    private final AuthenticationUseCase auth;

    public PageHandler(AuthenticationUseCase auth) {
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            sendBytes(ex, 405, TYPES.get("html"), "Método no permitido".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        String path = ex.getRequestURI().getPath();
        boolean loggedIn = sessionToken(ex).flatMap(auth::currentUser).isPresent();

        if (path.equals("/")) {
            redirect(ex, loggedIn ? "/inicio" : "/login");
        } else if (path.equals("/inicio")) {
            if (!loggedIn) {
                redirect(ex, "/login?motivo=sesion");   // pantalla protegida
            } else {
                serve(ex, "inicio.html", false);
            }
        } else if (PUBLIC_PAGES.containsKey(path)) {
            if (loggedIn && !path.equals("/restablecer")) {
                redirect(ex, "/inicio");
            } else {
                serve(ex, PUBLIC_PAGES.get(path), false);
            }
        } else if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.startsWith("/vendor/") || path.startsWith("/fonts/")
                || path.equals("/favicon.svg")) {
            serve(ex, path.substring(1), true);
        } else {
            notFound(ex);
        }
    }

    private void serve(HttpExchange ex, String resource, boolean cacheable) throws IOException {
        // Defensa contra path traversal: solo nombres simples dentro de /static
        if (resource.contains("..") || resource.contains("\\") || resource.contains("//")) {
            notFound(ex);
            return;
        }
        try (InputStream in = PageHandler.class.getResourceAsStream("/static/" + resource)) {
            if (in == null) {
                notFound(ex);
                return;
            }
            String ext = resource.substring(resource.lastIndexOf('.') + 1);
            sendBytes(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes(), cacheable);
        }
    }

    private void notFound(HttpExchange ex) throws IOException {
        try (InputStream in = PageHandler.class.getResourceAsStream("/static/404.html")) {
            byte[] body = in == null ? "No encontrado".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
            sendBytes(ex, 404, TYPES.get("html"), body, false);
        }
    }
}
