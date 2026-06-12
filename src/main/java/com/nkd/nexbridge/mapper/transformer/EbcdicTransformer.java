package com.nkd.nexbridge.mapper.transformer;

import java.nio.charset.Charset;

public class EbcdicTransformer {

    private static final Charset EBCDIC = Charset.forName("IBM037");

    public static String ebcdicToUtf8(byte[] ebcdicBytes) {
        return new String(ebcdicBytes, EBCDIC).trim();
    }

    public static byte[] utf8ToEbcdic(String value, int length) {
        byte[] ebcdic = value.getBytes(EBCDIC);
        byte[] result = new byte[length];
        // pad with EBCDIC space (0x40)
        java.util.Arrays.fill(result, (byte) 0x40);
        System.arraycopy(ebcdic, 0, result, 0, Math.min(ebcdic.length, length));
        return result;
    }
}
