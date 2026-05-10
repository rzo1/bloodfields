package com.github.rzo1.bloodfields.cli;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void escapeHandlesQuotesAndBackslashes() {
        assertEquals("\"hello\"", Json.escape("hello"));
        assertEquals("\"he\\\"lo\"", Json.escape("he\"lo"));
        assertEquals("\"a\\\\b\"", Json.escape("a\\b"));
        assertEquals("\"\\n\\t\\r\"", Json.escape("\n\t\r"));
    }

    @Test
    void escapeNullSafe() {
        assertEquals("null", Json.escape(null));
    }

    @Test
    void escapeControlChars() {
        String esc = Json.escape("");
        assertTrue(esc.contains("\\u0001"));
    }

    @Test
    void numberFormattingTrimsIntegerLikeDoubles() {
        assertEquals("5", Json.num(5.0));
        assertEquals("3.14", Json.num(3.14));
        assertEquals("42", Json.num(42L));
    }

    @Test
    void objBuilderProducesValidJson() {
        String s = Json.obj().str("a", "x").num("b", 7L).bool("c", true).build();
        assertEquals("{\"a\":\"x\",\"b\":7,\"c\":true}", s);
    }

    @Test
    void arrBuilderProducesValidJson() {
        String s = Json.arr().add("1").add("2").addStr("hello").build();
        assertEquals("[1,2,\"hello\"]", s);
    }

    @Test
    void parseObjectRoundTrip() {
        String src = "{\"op\":\"place\",\"type\":\"INFANTRY\",\"x\":100,\"y\":600.5,\"flag\":true}";
        Map<String, Object> m = Json.parseObject(src);
        assertEquals("place", m.get("op"));
        assertEquals("INFANTRY", m.get("type"));
        assertEquals(100L, m.get("x"));
        assertEquals(600.5, m.get("y"));
        assertEquals(Boolean.TRUE, m.get("flag"));
    }

    @Test
    void parseEmptyObjectAndArray() {
        Map<String, Object> obj = Json.parseObject("{}");
        assertTrue(obj.isEmpty());
        Object arr = Json.parse("[]");
        assertTrue(arr instanceof List);
        assertTrue(((List<?>) arr).isEmpty());
    }

    @Test
    void parseEscapedString() {
        Object v = Json.parse("\"a\\\"b\\nc\"");
        assertEquals("a\"b\nc", v);
    }

    @Test
    void parseNullValue() {
        Map<String, Object> m = Json.parseObject("{\"k\":null}");
        assertTrue(m.containsKey("k"));
        assertNull(m.get("k"));
    }

    @Test
    void parseArrayOfObjects() {
        Object v = Json.parse("[{\"a\":1},{\"a\":2}]");
        assertTrue(v instanceof List);
        List<?> list = (List<?>) v;
        assertEquals(2, list.size());
        assertNotNull(list.get(0));
    }

    @Test
    void parseRejectsTrailingGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{} extra"));
    }

    @Test
    void parseRejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{"));
    }

    @Test
    void buildersAreFluent() {
        Json.Obj o = Json.obj();
        Json.Obj r = o.str("x", "y");
        assertTrue(r == o);
        assertFalse(o.build().isEmpty());
    }
}
