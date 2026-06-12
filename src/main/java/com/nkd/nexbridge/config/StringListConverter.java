package com.nkd.nexbridge.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter(autoApply = true)
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        return "{" + String.join(",", attribute.stream()
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .toList()) + "}";
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        String inner = dbData.replaceAll("^\\{|\\}$", "");
        if (inner.isBlank()) return List.of();
        return Arrays.stream(inner.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
                .map(s -> s.replaceAll("^\"|\"$", ""))
                .toList();
    }
}
