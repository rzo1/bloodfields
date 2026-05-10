package com.github.rzo1.bloodfields.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {}

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    public static String num(long n) {
        return Long.toString(n);
    }

    public static String bool(boolean b) {
        return b ? "true" : "false";
    }

    public static Obj obj() {
        return new Obj();
    }

    public static Arr arr() {
        return new Arr();
    }

    public static final class Obj {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        public Obj kv(String k, String rawJsonValue) {
            sep();
            sb.append(escape(k)).append(':').append(rawJsonValue);
            return this;
        }

        public Obj str(String k, String v) {
            return kv(k, escape(v));
        }

        public Obj num(String k, double v) {
            return kv(k, Json.num(v));
        }

        public Obj num(String k, long v) {
            return kv(k, Json.num(v));
        }

        public Obj bool(String k, boolean v) {
            return kv(k, Json.bool(v));
        }

        private void sep() {
            if (!first) sb.append(',');
            first = false;
        }

        public String build() {
            sb.append('}');
            return sb.toString();
        }

        @Override public String toString() { return build(); }
    }

    public static final class Arr {
        private final StringBuilder sb = new StringBuilder("[");
        private boolean first = true;

        public Arr add(String rawJsonValue) {
            if (!first) sb.append(',');
            first = false;
            sb.append(rawJsonValue);
            return this;
        }

        public Arr addStr(String s) {
            return add(escape(s));
        }

        public String build() {
            sb.append(']');
            return sb.toString();
        }

        @Override public String toString() { return build(); }
    }

    public static Map<String, Object> parseObject(String s) {
        Parser p = new Parser(s);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("trailing input at " + p.pos);
        }
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("expected object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    public static Object parse(String s) {
        Parser p = new Parser(s);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("trailing input at " + p.pos);
        }
        return v;
    }

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) { this.src = src; this.pos = 0; }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        Object readValue() {
            skipWs();
            if (pos >= src.length()) throw new IllegalArgumentException("unexpected end");
            char c = src.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBool();
            if (c == 'n') return readNull();
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
            throw new IllegalArgumentException("unexpected char '" + c + "' at " + pos);
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object v = readValue();
                m.put(key, v);
                skipWs();
                char nx = peek();
                if (nx == ',') { pos++; continue; }
                if (nx == '}') { pos++; return m; }
                throw new IllegalArgumentException("expected , or } at " + pos);
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> a = new java.util.ArrayList<>();
            skipWs();
            if (peek() == ']') { pos++; return a; }
            while (true) {
                a.add(readValue());
                skipWs();
                char nx = peek();
                if (nx == ',') { pos++; continue; }
                if (nx == ']') { pos++; return a; }
                throw new IllegalArgumentException("expected , or ] at " + pos);
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("bad escape");
                    char e = src.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("bad \\u escape");
                            int cp = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            sb.append((char) cp);
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        Object readBool() {
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("expected bool at " + pos);
        }

        Object readNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw new IllegalArgumentException("expected null at " + pos);
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else break;
            }
            String tok = src.substring(start, pos);
            if (tok.contains(".") || tok.contains("e") || tok.contains("E")) {
                return Double.parseDouble(tok);
            }
            try {
                return Long.parseLong(tok);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(tok);
            }
        }

        void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + pos);
            }
            pos++;
        }

        char peek() {
            if (pos >= src.length()) throw new IllegalArgumentException("unexpected end");
            return src.charAt(pos);
        }
    }
}
