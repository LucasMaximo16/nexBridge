package com.nkd.nexbridge.mapper.transformer;

public class PadTransformer {

    public static String padRight(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return String.format("%-" + length + "s", value).replace(' ', padChar);
    }

    public static String padLeft(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(value.length() - length);
        return String.format("%" + length + "s", value).replace(' ', padChar);
    }

    public static String padLeftZero(String value, int length) {
        return padLeft(value, length, '0');
    }

    public static String uppercasePadRight(String value, int length) {
        if (value == null) value = "";
        return padRight(value.toUpperCase(), length, ' ');
    }
}
