package com.ninecookies.wiremock.extensions.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.jayway.jsonpath.DocumentContext;
import com.ninecookies.wiremock.extensions.util.Placeholder;
import com.ninecookies.wiremock.extensions.util.Placeholders;

public class PlaceholderTest {

    @Test
    public void testPlaceholder() {
        String value = "blubber";
        String substitute = "blubber";
        String json = "{\"blubb\":\"" + value + "\"}";
        DocumentContext dc = Placeholders.documentContextOf(json);
        String pattern = "$(blubb)";
        String placeholder = "$(blubb)";
        Placeholder p = Placeholder.of(pattern);
        assertEquals(p.getPattern(), pattern);
        assertEquals(p.getSubstitute(dc), substitute);
        assertEquals(p.getPlaceholder(), placeholder);
        assertEquals(p.getValue(json), value);
        assertNull(p.getValue(""));
        assertNull(p.getValue((String) null));

        pattern = "bla $(blubb) blubber";
        substitute = "bla blubber blubber";
        p = Placeholder.of(pattern);
        assertEquals(p.getPattern(), pattern);
        assertEquals(p.getSubstitute(dc), substitute);
        assertEquals(p.getPlaceholder(), placeholder);
        assertEquals(p.getValue(json), value);
        assertNull(p.getValue(""));
        assertNull(p.getValue((String) null));

        assertThrows(IllegalArgumentException.class, () -> Placeholder.of(""));
        assertThrows(IllegalArgumentException.class, () -> Placeholder.of(null));
        assertThrows(IllegalArgumentException.class, () -> Placeholder.of("blubber bla"));

        assertTrue(Placeholder.containsPattern("$(blubb)"));
        assertTrue(Placeholder.containsPattern("bla $(blubb) blubber"));
        assertFalse(Placeholder.containsPattern("blubber bla"));
        assertFalse(Placeholder.containsPattern(""));
        assertFalse(Placeholder.containsPattern(null));
    }
}
