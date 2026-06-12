package com.nkd.nexbridge.mapper.transformer;

public class RemoveSpecialCharsTransformer {

    public static String removeSpecialChars(String value) {
        if (value == null) return null;
        return value.replaceAll("[.\\-/()\\s]", "");
    }
}
