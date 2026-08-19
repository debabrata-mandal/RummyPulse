package com.example.rummypulse.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GroqGameNameServiceTest {

    @Test
    public void createRequestBody_usesGptOssReasoningSettings() throws Exception {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", "Generate a name");

        JSONObject body = GroqGameNameService.createRequestBody("openai/gpt-oss-20b", message);

        assertEquals("openai/gpt-oss-20b", body.getString("model"));
        assertEquals(0.6, body.getDouble("temperature"), 0.0);
        assertEquals(128, body.getInt("max_completion_tokens"));
        assertEquals("low", body.getString("reasoning_effort"));
        assertFalse(body.getBoolean("include_reasoning"));
        assertFalse(body.has("max_tokens"));

        JSONArray messages = body.getJSONArray("messages");
        assertEquals(1, messages.length());
        assertEquals("Generate a name", messages.getJSONObject(0).getString("content"));
    }
}
