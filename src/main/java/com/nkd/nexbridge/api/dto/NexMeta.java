package com.nkd.nexbridge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class NexMeta {
    @JsonProperty("trace_id")
    private String traceId;
    private String timestamp;
    @JsonProperty("source_system")
    private String sourceSystem;
    @JsonProperty("source_connector")
    private String sourceConnector;
    @JsonProperty("copybook_version")
    private String copybookVersion;
    @JsonProperty("processing_ms")
    private Integer processingMs;
    @JsonProperty("masked_fields")
    private List<String> maskedFields;
    @JsonProperty("discarded_fields")
    private List<String> discardedFields;
    @JsonProperty("nexbridge_version")
    private String nexbridgeVersion;
}
