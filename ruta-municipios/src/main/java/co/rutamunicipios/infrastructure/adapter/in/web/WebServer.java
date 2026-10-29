package co.rutamunicipios.infrastructure.adapter.in.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP del JDK (com.sun.net.httpserver) con HILOS VIRTUALES (Java 21, JEP 444):
 * cada petición corre en un hilo virtual barato, sin pool que configurar.
 */
public final class WebServer {

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public WebServer(int port, ApiHandler api, PageHandler pages, HttpHandler devMailbox) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", api);
        if (devMailbox != null) server.createContext("/dev/", devMailbox);
        server.createContext("/", pages);
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        executor.shutdown();
    }
}
