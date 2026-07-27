package com.example.rummypulse.data;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * URL form encoding that remains compatible with Android versions before API 33.
 */
final class Utf8UrlCodec {
    private static final String UTF_8 = "UTF-8";

    private Utf8UrlCodec() {
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value, UTF_8);
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable.", impossible);
        }
    }

    static String decode(String value) {
        try {
            return URLDecoder.decode(value, UTF_8);
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable.", impossible);
        }
    }
}
