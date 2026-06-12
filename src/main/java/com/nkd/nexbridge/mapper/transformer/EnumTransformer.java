package com.nkd.nexbridge.mapper.transformer;

import java.util.Map;

public class EnumTransformer {

    public static String map(String value, Map<String, String> enumMap) {
        if (value == null || enumMap == null) return value;
        return enumMap.getOrDefault(value.trim(), value);
    }

    public static String reverseMap(String value, Map<String, String> enumMap) {
        if (value == null || enumMap == null) return value;
        return enumMap.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(value))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(value);
    }
}
