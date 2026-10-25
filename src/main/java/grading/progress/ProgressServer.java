package grading.progress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** A small web server that serves the dashboard page and the progress data. */
public final class ProgressServer {

    private final HttpServer server;

    /**
     * Creates the server without starting it.
     *
     * @param tracker the progress object whose JSON is served at /api/progress
     * @param port    the port to listen on, such as 8080
     * @throws IOException if the port is already in use
     */
    public ProgressServer(ProgressTracker tracker, int port) throws IOException {
        // 127.0.0.1 means only this computer can connect, not other machines on the network.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/progress", exchange ->
                send(exchange, "application/json", tracker.toJson().getBytes(StandardCharsets.UTF_8)));
        // The page's libraries, packaged with the application so no internet connection is needed.
        server.createContext("/lib/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring("/lib/".length());
            try (InputStream file = name.matches("[\\w.-]+")
                    ? ProgressServer.class.getResourceAsStream("/web/lib/" + name) : null) {
                if (file == null) {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                    exchange.close();
                    return;
                }
                send(exchange, name.endsWith(".css") ? "text/css" : "text/javascript", file.readAllBytes());
            }
        });
        server.createContext("/", exchange -> {
            try (InputStream page = ProgressServer.class.getResourceAsStream("/web/index.html")) {
                send(exchange, "text/html", page.readAllBytes());
            }
        });
    }

    /** Starts listening in a background thread. */
    public void start() {
        server.start();
    }

    /** Stops listening. Called when the application closes. */
    public void stop() {
        server.stop(0);
    }

    /**
     * Sends one response.
     *
     * @param exchange the request and response pair from the server
     * @param type     the content type, such as "application/json"
     * @param body     the bytes to send
     */
    private static void send(HttpExchange exchange, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}