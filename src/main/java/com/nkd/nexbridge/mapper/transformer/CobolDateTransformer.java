package com.nkd.nexbridge.mapper.transformer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CobolDateTransformer {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter YYMMDD   = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static LocalDate cobolToIso(String cobolDate) {
        if (cobolDate == null || cobolDate.isBlank()) return null;
        return LocalDate.parse(cobolDate.trim(), YYYYMMDD);
    }

    public static LocalDate ddmmyyyyToIso(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date.trim(), DDMMYYYY);
    }

    public static LocalDate yymmddToIso(String date) {
        if (date == null || date.isBlank()) return null;
        LocalDate d = LocalDate.parse("20" + date.trim(), YYYYMMDD);
        return d;
    }

    public static LocalDateTime timestampCobolToIso(String ts) {
        if (ts == null || ts.isBlank()) return null;
        return LocalDateTime.parse(ts.trim(), TIMESTAMP);
    }

    public static String isoToCobol(LocalDate date) {
        if (date == null) return "00000000";
        return date.format(YYYYMMDD);
    }
}
