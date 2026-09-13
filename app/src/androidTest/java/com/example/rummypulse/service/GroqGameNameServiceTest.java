package com.example.rummypulse.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GroqGameNameServiceTest {

    @Test
    public void extractName_acceptsValidCallableResponse() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("name", "  Royal Rummy  ");

        assertEquals("Royal Rummy", GroqGameNameService.extractName(response));
    }

    @Test
    public void extractName_rejectsMissingName() {
        assertThrows(IOException.class,
                () -> GroqGameNameService.extractName(Collections.emptyMap()));
    }

    @Test
    public void extractName_rejectsUnexpectedCharacters() {
        assertThrows(IOException.class,
                () -> GroqGameNameService.extractName(
                        Collections.singletonMap("name", "Rummy! 123")));
    }
}
