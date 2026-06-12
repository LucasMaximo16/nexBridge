package com.nkd.nexbridge.mapper;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class MappingConfig {
    private String mappingId;
    private String version;
    private String connectorId;
    private String copybookId;
    private String sourceFormat;
    private String targetFormat;
    private List<FieldMapping> requestFields;
    private List<FieldMapping> responseFields;
}
