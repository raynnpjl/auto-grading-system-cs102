package grading.common;

import java.util.List;
import java.util.stream.Collectors;

/** Builds the small pieces of JSON the web page reads. */
public final class Json {

    private Json() {
    }

    /** Wraps text in quotes and escapes characters that are not allowed raw in JSON. */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** A JSON array of the given texts. */
    public static String strings(List<String> values) {
        return values.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));
    }

    /** A JSON array of values that are already JSON, such as objects. */
    public static String array(List<String> jsonValues) {
        return jsonValues.stream().collect(Collectors.joining(",", "[", "]"));
    }

    /** The {"error": message} object the page shows to the user. */
    public static String error(String message) {
        return "{\"error\":" + quote(message) + "}";
    }
}
