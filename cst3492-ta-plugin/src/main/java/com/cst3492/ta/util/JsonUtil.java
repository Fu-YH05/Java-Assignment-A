package com.cst3492.ta.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared Jackson mapper. */
public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
