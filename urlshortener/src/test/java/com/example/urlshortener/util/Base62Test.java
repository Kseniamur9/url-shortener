package com.example.urlshortener.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    @Test
    void encodeOne() {
        assertEquals("1", Base62.encode(1));
    }

    @Test
    void encodeTen() {
        assertEquals("a", Base62.encode(10));
    }

    @Test
    void encodeZero() {
        assertEquals("0", Base62.encode(0));
    }

    @Test
    void encodeLargeNumber() {
        String code = Base62.encode(1_000_000L);
        assertNotNull(code);
        assertTrue(code.length() <= 10);
    }
}