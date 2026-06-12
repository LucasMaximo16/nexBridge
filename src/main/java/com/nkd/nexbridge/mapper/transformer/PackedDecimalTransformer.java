package com.nkd.nexbridge.mapper.transformer;

import java.math.BigDecimal;

public class PackedDecimalTransformer {

    public static BigDecimal packedToDecimal(byte[] packed, int decimalPlaces) {
        if (packed == null || packed.length == 0) return BigDecimal.ZERO;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < packed.length - 1; i++) {
            int b = packed[i] & 0xFF;
            digits.append((b >> 4) & 0x0F);
            digits.append(b & 0x0F);
        }
        int lastByte = packed[packed.length - 1] & 0xFF;
        digits.append((lastByte >> 4) & 0x0F);
        int sign = lastByte & 0x0F;
        boolean negative = (sign == 0x0D || sign == 0x0B);

        String raw = digits.toString();
        BigDecimal value;
        if (decimalPlaces > 0 && raw.length() > decimalPlaces) {
            String intPart = raw.substring(0, raw.length() - decimalPlaces);
            String decPart = raw.substring(raw.length() - decimalPlaces);
            value = new BigDecimal(intPart + "." + decPart);
        } else {
            value = new BigDecimal(raw);
        }
        return negative ? value.negate() : value;
    }

    public static byte[] decimalToPacked(BigDecimal value, int decimalPlaces) {
        if (value == null) value = BigDecimal.ZERO;
        boolean negative = value.signum() < 0;
        value = value.abs().movePointRight(decimalPlaces);
        String digits = value.toBigInteger().toString();
        // ensure odd number of digits for proper packing
        if (digits.length() % 2 == 0) digits = "0" + digits;
        int packedLen = (digits.length() + 1) / 2;
        byte[] result = new byte[packedLen];
        int d = 0;
        for (int i = 0; i < packedLen - 1; i++) {
            int high = digits.charAt(d++) - '0';
            int low = digits.charAt(d++) - '0';
            result[i] = (byte) ((high << 4) | low);
        }
        int lastDigit = digits.charAt(d) - '0';
        int sign = negative ? 0x0D : 0x0C;
        result[packedLen - 1] = (byte) ((lastDigit << 4) | sign);
        return result;
    }
}
