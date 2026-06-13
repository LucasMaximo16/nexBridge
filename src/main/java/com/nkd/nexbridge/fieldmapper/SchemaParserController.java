package com.nkd.nexbridge.fieldmapper;

import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import com.nkd.nexbridge.config.NexBridgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/schema")
@RequiredArgsConstructor
public class SchemaParserController {

    private final SchemaParserService schemaParserService;
    private final NexBridgeProperties properties;

    @PostMapping("/parse/xsd")
    public ResponseEntity<NexResponse<List<SchemaField>>> parseXsd(@RequestBody String xsdContent) {
        List<SchemaField> fields = schemaParserService.parseXsd(xsdContent);
        return ResponseEntity.ok(NexResponse.ok(fields, buildMeta()));
    }

    @PostMapping("/parse/wsdl")
    public ResponseEntity<NexResponse<WsdlParseResult>> parseWsdl(@RequestBody String wsdlContent) {
        WsdlParseResult result = schemaParserService.parseWsdl(wsdlContent);
        return ResponseEntity.ok(NexResponse.ok(result, buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .traceId(TraceIdFilter.current())
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
