package web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Reads request parameters and writes responses for the web application's handlers. */
final class HttpResponses {

    private static final String JSON = "application/json; charset=UTF-8";
    private static final String HTML = "text/html; charset=UTF-8";
    static final String CSS = "text/css; charset=UTF-8";
    static final String JAVASCRIPT = "text/javascript; charset=UTF-8";
    static final String ZIP = "application/zip";
    static final String PDF = "application/pdf";
    static final String CSV = "text/csv; charset=UTF-8";
    static final String PLAIN_TEXT = "text/plain; charset=UTF-8";
    /** Where the page's libraries are packaged. */
    static final String LIBRARIES = "/web/lib/";
    private static final String PAGE = "/web/index.html";

    private HttpResponses() {
    }

    /** Sends the single page of the web application. */
    static void sendPage(HttpExchange exchange) throws IOException {
        sendResource(exchange, PAGE, HTML);
    }

    /**
     * Sends a file packaged with the application, such as a library the page uses, so the page
     * works without an internet connection.
     */
    static void sendResource(HttpExchange exchange, String resource, String type) throws IOException {
        try (InputStream input = HttpResponses.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException(resource + " is missing from the build");
            }
            send(exchange, HttpURLConnection.HTTP_OK, type, input.readAllBytes(), Map.of());
        }
    }

    /** Sends a JSON body with the given status. */
    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, JSON, json.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /** Sends a 200 response with a JSON body. */
    static void sendJson(HttpExchange exchange, String json) throws IOException {
        sendJson(exchange, HttpURLConnection.HTTP_OK, json);
    }

    /** Sends {"error": message} with the given status, for the page to show. */
    static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Json.error(message));
    }

    /** Sends a file as a download. */
    static void sendFile(HttpExchange exchange, String type, byte[] bytes, String fileName) throws IOException {
        send(exchange, HttpURLConnection.HTTP_OK, type, bytes, Map.of(
                "Content-Disposition", "attachment; filename=\"" + fileName + "\""));
    }

    /** The decoded query parameters of the request, such as ?name=sheet.csv. */
    static Map<String, String> query(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        return parseForm(query == null ? "" : query);
    }

    /** The decoded fields of a form posted as the request body. */
    static Map<String, String> form(HttpExchange exchange) throws IOException {
        return parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    /** Reads "a=1&b=2" into a map, decoding the %20-style escapes. */
    private static Map<String, String> parseForm(String text) {
        Map<String, String> values = new HashMap<>();
        for (String pair : text.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                String key = URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                values.put(key, value);
            }
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes,
            Map<String, String> headers) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
