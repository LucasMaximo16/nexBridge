package com.nkd.nexbridge.governance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/compliance")
@RequiredArgsConstructor
@Slf4j
public class ComplianceController {

    private final ComplianceExportService complianceExportService;

    /**
     * GET /api/admin/compliance/export/csv
     * Returns a CSV compliance report for the given date range (and optional connectorId).
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String connectorId) {

        log.info("Compliance CSV export requested: from={} to={} connectorId={}", from, to, connectorId);

        byte[] data = complianceExportService.exportCsv(from, to, connectorId);

        String filename = "compliance-" + from + "-" + to + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length))
                .body(data);
    }

    /**
     * GET /api/admin/compliance/export/pdf
     * Returns a PDF compliance report for the given date range (and optional connectorId).
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String connectorId) {

        log.info("Compliance PDF export requested: from={} to={} connectorId={}", from, to, connectorId);

        byte[] data = complianceExportService.exportPdf(from, to, connectorId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length))
                .body(data);
    }
}
