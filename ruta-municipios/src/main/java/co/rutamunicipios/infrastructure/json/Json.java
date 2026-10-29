package co.rutamunicipios.infrastructure.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector/escritor JSON mínimo (RFC 8259) para no depender de librerías externas.
 * Tipos: Map (objeto), List (arreglo), String, Double/Long (número), Boolean, null.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ escritura
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case String s -> writeString(sb, s);
            case Boolean b -> sb.append(b);
            case Double d when d.isNaN() || d.isInfinite() -> sb.append("null");
            case Number n -> sb.append(n);
            case Map<?, ?> m -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(sb, String.valueOf(e.getKey()));
                    sb.append(':');
                    writeValue(sb, e.getValue());
                }
                sb.append('}');
            }
            case Iterable<?> it -> {
                sb.append('[');
                boolean first = true;
                for (Object o : it) {
                    if (!first) sb.append(',');
                    first = false;
                    writeValue(sb, o);
                }
                sb.append(']');
            }
            default -> writeString(sb, v.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c"); // seguro para incrustar en HTML
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ lectura
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != text.length()) throw new IllegalArgumentException("JSON con contenido extra");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text == null || text.isBlank() ? "{}" : text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("Se esperaba un objeto JSON");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int pos;
        private int depth;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object value() {
            if (pos >= s.length()) throw error("fin inesperado");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            if (++depth > 64) throw error("anidación excesiva");
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            skipWs();
            if (peek() == '}') { pos++; depth--; return m; }
            while (true) {
                skipWs();
                if (peek() != '"') throw error("se esperaba una clave");
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                m.put(key, value());
                skipWs();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw error("se esperaba ',' o '}'");
            }
            depth--;
            return m;
        }

        List<Object> array() {
            if (++depth > 64) throw error("anidación excesiva");
            List<Object> list = new ArrayList<>();
            pos++;
            skipWs();
            if (peek() == ']') { pos++; depth--; return list; }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw error("se esperaba ',' o ']'");
            }
            depth--;
            return list;
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw error("escape unicode incompleto");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw error("escape inválido");
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String n = s.substring(start, pos);
            if (n.isEmpty()) throw error("valor inesperado");
            try {
                if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
                return Long.parseLong(n);
            } catch (NumberFormatException e) {
                throw error("número inválido");
            }
        }

        Object literal(String word, Object value) {
            if (!s.startsWith(word, pos)) throw error("literal inválido");
            pos += word.length();
            return value;
        }

        char peek() {
            if (pos >= s.length()) throw error("fin inesperado");
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            if (next() != c) throw error("se esperaba '" + c + "'");
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON inválido en posición " + pos + ": " + msg);
        }
    }
}
