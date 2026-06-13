package com.nkd.nexbridge.governance;

import com.nkd.nexbridge.audit.AuditEntry;
import com.nkd.nexbridge.audit.AuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceExportService {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of("cpf", "cnpj", "conta", "salario");
    private static final String MASKED = "***MASKED***";
    private static final int PAGE_SIZE = 500;

    private final AuditRepository auditRepository;

    // -------------------------------------------------------------------------
    // CSV Export
    // -------------------------------------------------------------------------

    public byte[] exportCsv(LocalDate from, LocalDate to, String connectorId) {
        List<AuditEntry> entries = fetchEntries(from, to, connectorId);

        StringBuilder sb = new StringBuilder();
        sb.append("traceId,timestamp,operation,sourceSystem,status,maskedFields,discardedFields\n");

        for (AuditEntry e : entries) {
            sb.append(csvField(e.getTraceId())).append(",");
            sb.append(csvField(e.getTimestampUtc() != null ? e.getTimestampUtc().toString() : "")).append(",");
            sb.append(csvField(buildOperation(e))).append(",");
            sb.append(csvField(maskIfSensitive(e.getSourceSystem()))).append(",");
            sb.append(csvField(e.getResult())).append(",");
            sb.append(csvField(listToString(e.getSensitiveFields()))).append(",");
            sb.append(csvField(listToString(e.getDiscardedFields()))).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // PDF Export (minimal hand-crafted PDF — no external libraries)
    // -------------------------------------------------------------------------

    public byte[] exportPdf(LocalDate from, LocalDate to, String connectorId) {
        List<AuditEntry> entries = fetchEntries(from, to, connectorId);

        // Build content stream text
        StringBuilder content = new StringBuilder();
        content.append("BT\n");
        content.append("/F1 10 Tf\n");

        // Title
        content.append("50 800 Td\n");
        content.append("(NexBridge Compliance Export - ").append(from).append(" to ").append(to).append(") Tj\n");

        // Header
        content.append("0 -20 Td\n");
        content.append("(traceId | timestamp | operation | sourceSystem | status | maskedFields | discardedFields) Tj\n");
        content.append("0 -5 Td\n");
        content.append("(---------------------------------------------------------------------------) Tj\n");

        // Rows — each row moves down 14 points; start a new page approximately every 50 rows
        int rowsPerPage = 50;
        int rowCount = 0;
        for (AuditEntry e : entries) {
            if (rowCount > 0 && rowCount % rowsPerPage == 0) {
                // simple continuation note (full multi-page PDF is complex; indicate overflow)
                content.append("0 -14 Td\n");
                content.append("([... continued ...]) Tj\n");
            }
            String line = buildPdfRow(e);
            content.append("0 -14 Td\n");
            content.append("(").append(pdfEscape(line)).append(") Tj\n");
            rowCount++;
        }

        content.append("ET\n");

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.US_ASCII);

        // Build PDF structure
        List<byte[]> objects = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();

        // obj 1: catalog
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        // obj 2: pages
        objects.add("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        // obj 3: page
        objects.add(("3 0 obj\n<< /Type /Page /Parent 2 0 R "
                + "/MediaBox [0 0 842 595] "
                + "/Contents 5 0 R "
                + "/Resources << /Font << /F1 4 0 R >> >> >>\nendobj\n")
                .getBytes(StandardCharsets.US_ASCII));
        // obj 4: font
        objects.add(("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n")
                .getBytes(StandardCharsets.US_ASCII));
        // obj 5: content stream
        String streamHeader = "5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n";
        String streamFooter = "\nendstream\nendobj\n";
        byte[] streamHeaderBytes = streamHeader.getBytes(StandardCharsets.US_ASCII);
        byte[] streamFooterBytes = streamFooter.getBytes(StandardCharsets.US_ASCII);

        // Assemble PDF
        byte[] pdfHeader = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        // Calculate sizes for xref
        int offset = pdfHeader.length;
        int[] objOffsets = new int[5];

        for (int i = 0; i < objects.size(); i++) {
            objOffsets[i] = offset;
            offset += objects.get(i).length;
        }
        int contentObjOffset = offset;
        objOffsets[4] = contentObjOffset;
        offset += streamHeaderBytes.length + contentBytes.length + streamFooterBytes.length;

        int xrefOffset = offset;

        // xref table
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n");
        xref.append("0 6\n");
        xref.append("0000000000 65535 f \n");
        for (int i = 0; i < 5; i++) {
            xref.append(String.format("%010d 00000 n \n", objOffsets[i]));
        }

        String trailer = "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n";

        // Write to output
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            baos.write(pdfHeader);
            for (int i = 0; i < objects.size(); i++) {
                baos.write(objects.get(i));
            }
            baos.write(streamHeaderBytes);
            baos.write(contentBytes);
            baos.write(streamFooterBytes);
            baos.write(xref.toString().getBytes(StandardCharsets.US_ASCII));
            baos.write(trailer.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            log.warn("PDF assembly error: {}", ex.getMessage());
        }

        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Data fetching
    // -------------------------------------------------------------------------

    private List<AuditEntry> fetchEntries(LocalDate from, LocalDate to, String connectorId) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<AuditEntry> results = new ArrayList<>();
        int page = 0;
        Page<AuditEntry> pageResult;

        do {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("timestampUtc").ascending());
            pageResult = auditRepository.findByFilters(null, null, null, fromInstant, toInstant, pageable);
            for (AuditEntry e : pageResult.getContent()) {
                if (connectorId == null || connectorId.isBlank() || connectorId.equals(e.getConnectorId())) {
                    results.add(e);
                }
            }
            page++;
        } while (pageResult.hasNext());

        return results;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildOperation(AuditEntry e) {
        String method = e.getMethod() != null ? e.getMethod() : "";
        String endpoint = e.getEndpoint() != null ? e.getEndpoint() : "";
        return method + " " + endpoint;
    }

    private String buildPdfRow(AuditEntry e) {
        return (e.getTraceId() != null ? e.getTraceId() : "") + " | "
                + (e.getTimestampUtc() != null ? e.getTimestampUtc().toString() : "") + " | "
                + buildOperation(e) + " | "
                + maskIfSensitive(e.getSourceSystem()) + " | "
                + (e.getResult() != null ? e.getResult() : "") + " | "
                + listToString(e.getSensitiveFields()) + " | "
                + listToString(e.getDiscardedFields());
    }

    private String maskIfSensitive(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase();
        for (String kw : SENSITIVE_KEYWORDS) {
            if (lower.contains(kw)) {
                return MASKED;
            }
        }
        return value;
    }

    private String listToString(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(";");
            String item = list.get(i);
            sb.append(maskIfSensitive(item));
        }
        return sb.toString();
    }

    private String csvField(String value) {
        if (value == null) return "";
        // Escape CSV: wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String pdfEscape(String value) {
        if (value == null) return "";
        // PDF string literals: escape parentheses and backslash; strip non-ASCII
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
