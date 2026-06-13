package com.nkd.nexbridge.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formats routing payload data into JSON, XML, or CSV string representations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayloadFormatter {

    private final ObjectMapper objectMapper;

    /**
     * Format the given data map into the specified format string.
     *
     * @param data   the payload data
     * @param format "JSON", "XML", "CSV", or null (defaults to JSON)
     * @return formatted string
     */
    public String format(Map<String, Object> data, String format) {
        if (format == null) {
            return toJson(data);
        }
        return switch (format.toUpperCase()) {
            case "XML" -> toXml(data);
            case "CSV" -> toCsv(data);
            default -> toJson(data);
        };
    }

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("PayloadFormatter: JSON serialization failed, falling back to toString: {}", e.getMessage());
            return data != null ? data.toString() : "{}";
        }
    }

    // -------------------------------------------------------------------------
    // XML
    // -------------------------------------------------------------------------

    private String toXml(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<response>\n");
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String tagName = sanitizeXmlTagName(entry.getKey());
                sb.append("  <").append(tagName).append(">")
                  .append(escapeXml(String.valueOf(entry.getValue())))
                  .append("</").append(tagName).append(">\n");
            }
        }
        sb.append("</response>");
        return sb.toString();
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeXmlTagName(String key) {
        if (key == null || key.isEmpty()) return "field";
        // Replace characters that are invalid in XML element names with underscore
        return key.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }

    // -------------------------------------------------------------------------
    // CSV
    // -------------------------------------------------------------------------

    private String toCsv(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        // Flatten nested maps using dot notation
        Map<String, String> flat = new LinkedHashMap<>();
        flattenMap("", data, flat);

        StringBuilder header = new StringBuilder();
        StringBuilder values = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            if (!first) {
                header.append(",");
                values.append(",");
            }
            header.append(csvEscape(entry.getKey()));
            values.append(csvEscape(entry.getValue()));
            first = false;
        }
        return header + "\n" + values;
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenMap(key, (Map<String, Object>) value, result);
            } else {
                result.put(key, value != null ? String.valueOf(value) : "");
            }
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        // If value contains comma, double-quote, or newline — wrap in double quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
