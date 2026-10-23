package com.minihttp.packet.codec;

import com.minihttp.packet.protocol.HttpParseException;
import com.minihttp.packet.protocol.HttpStatus;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FormUrlEncoded {
    private FormUrlEncoded() {
    }

    public static Map<String, List<String>> decode(String encoded) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return Map.of();
        }
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.computeIfAbsent(decodeComponent(key), _ -> new ArrayList<>())
                    .add(decodeComponent(value));
        }
        result.replaceAll((_, values) -> List.copyOf(values));
        return Collections.unmodifiableMap(result);
    }

    private static String decodeComponent(String component) {
        try {
            return URLDecoder.decode(component, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Malformed percent-encoding: " + component);
        }
    }
}
