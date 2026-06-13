package com.nkd.nexbridge.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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
public class AuditExportService {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of("cpf", "cnpj", "conta", "salario");
    private static final String MASKED = "***MASKED***";
    private static final int PAGE_SIZE = 500;

    private final AuditRepository auditRepository;

    // -------------------------------------------------------------------------
    // CSV Export
    // -------------------------------------------------------------------------

    public byte[] exportCsv(LocalDate from, LocalDate to, String traceId, String sourceSystem) {
        List<AuditEntry> entries = fetchEntries(from, to, traceId, sourceSystem);

        StringBuilder sb = new StringBuilder();
        sb.append("traceId,timestamp,operation,sourceSystem,sourceConnector,copybookVersion,status,maskedFields,discardedFields,processingMs,requestSummary\n");

        for (AuditEntry e : entries) {
            sb.append(csvField(e.getTraceId())).append(",");
            sb.append(csvField(e.getTimestampUtc() != null ? e.getTimestampUtc().toString() : "")).append(",");
            sb.append(csvField(buildOperation(e))).append(",");
            sb.append(csvField(maskIfSensitive(e.getSourceSystem()))).append(",");
            sb.append(csvField(e.getConnectorId())).append(",");
            sb.append(csvField(e.getCopybookVersion())).append(",");
            sb.append(csvField(e.getResult())).append(",");
            sb.append(csvField(listToString(e.getSensitiveFields()))).append(",");
            sb.append(csvField(listToString(e.getDiscardedFields()))).append(",");
            sb.append(csvField(e.getDurationMs() != null ? e.getDurationMs().toString() : "")).append(",");
            sb.append(csvField(buildRequestSummary(e))).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // PDF Export (minimal hand-crafted PDF — no external libraries)
    // -------------------------------------------------------------------------

    public byte[] exportPdf(LocalDate from, LocalDate to, String traceId, String sourceSystem) {
        List<AuditEntry> entries = fetchEntries(from, to, traceId, sourceSystem);

        StringBuilder content = new StringBuilder();
        content.append("BT\n");
        content.append("/F1 10 Tf\n");

        // Title
        content.append("50 800 Td\n");
        content.append("(NexBridge Audit Log Report) Tj\n");

        // Date range header
        content.append("0 -15 Td\n");
        content.append("(Date range: ").append(pdfEscape(from.toString())).append(" to ").append(pdfEscape(to.toString())).append(") Tj\n");

        // Column header
        content.append("0 -20 Td\n");
        content.append("(traceId | timestamp | operation | sourceSystem | sourceConnector | copybookVersion | status | processingMs) Tj\n");
        content.append("0 -5 Td\n");
        content.append("(---------------------------------------------------------------------------) Tj\n");

        // Rows
        int rowsPerPage = 48;
        int rowCount = 0;
        for (AuditEntry e : entries) {
            if (rowCount > 0 && rowCount % rowsPerPage == 0) {
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

        // Build PDF objects
        List<byte[]> objects = new ArrayList<>();

        // obj 1: catalog
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII));
        // obj 2: pages
        objects.add("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII));
        // obj 3: page
        objects.add(("3 0 obj\n<< /Type /Page /Parent 2 0 R "
                + "/MediaBox [0 0 842 595] "
                + "/Contents 5 0 R "
                + "/Resources << /Font << /F1 4 0 R >> >> >>\nendobj\n")
                .getBytes(StandardCharsets.US_ASCII));
        // obj 4: font
        objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII));

        // obj 5: content stream
        String streamHeader = "5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n";
        String streamFooter = "\nendstream\nendobj\n";
        byte[] streamHeaderBytes = streamHeader.getBytes(StandardCharsets.US_ASCII);
        byte[] streamFooterBytes = streamFooter.getBytes(StandardCharsets.US_ASCII);

        // Calculate offsets for xref
        byte[] pdfHeader = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        int offset = pdfHeader.length;
        int[] objOffsets = new int[5];

        for (int i = 0; i < objects.size(); i++) {
            objOffsets[i] = offset;
            offset += objects.get(i).length;
        }
        objOffsets[4] = offset;
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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(pdfHeader);
            for (byte[] obj : objects) {
                baos.write(obj);
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

    private List<AuditEntry> fetchEntries(LocalDate from, LocalDate to, String traceId, String sourceSystem) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // If traceId is specified, try direct lookup first
        if (traceId != null && !traceId.isBlank()) {
            return auditRepository.findByTraceId(traceId)
                    .filter(e -> !e.getTimestampUtc().isBefore(fromInstant)
                            && e.getTimestampUtc().isBefore(toInstant)
                            && (sourceSystem == null || sourceSystem.isBlank()
                                || sourceSystem.equals(e.getSourceSystem())))
                    .map(List::of)
                    .orElse(List.of());
        }

        List<AuditEntry> results = new ArrayList<>();
        int page = 0;
        Page<AuditEntry> pageResult;

        do {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("timestampUtc").ascending());
            pageResult = auditRepository.findByFilters(null, null, null, fromInstant, toInstant, pageable);
            for (AuditEntry e : pageResult.getContent()) {
                if (sourceSystem == null || sourceSystem.isBlank()
                        || sourceSystem.equals(e.getSourceSystem())) {
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
        return (method + " " + endpoint).trim();
    }

    private String buildRequestSummary(AuditEntry e) {
        StringBuilder sb = new StringBuilder();
        if (e.getHttpStatus() != null) {
            sb.append("HTTP ").append(e.getHttpStatus());
        }
        if (e.getRequestSizeBytes() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("req=").append(e.getRequestSizeBytes()).append("B");
        }
        if (e.getResponseSizeBytes() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("resp=").append(e.getResponseSizeBytes()).append("B");
        }
        return sb.toString();
    }

    private String buildPdfRow(AuditEntry e) {
        return (e.getTraceId() != null ? e.getTraceId() : "") + " | "
                + (e.getTimestampUtc() != null ? e.getTimestampUtc().toString() : "") + " | "
                + buildOperation(e) + " | "
                + maskIfSensitive(e.getSourceSystem()) + " | "
                + (e.getConnectorId() != null ? e.getConnectorId() : "") + " | "
                + (e.getCopybookVersion() != null ? e.getCopybookVersion() : "") + " | "
                + (e.getResult() != null ? e.getResult() : "") + " | "
                + (e.getDurationMs() != null ? e.getDurationMs().toString() : "");
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
            sb.append(maskIfSensitive(list.get(i)));
        }
        return sb.toString();
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String pdfEscape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
