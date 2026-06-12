package com.nkd.nexbridge.mapper;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class FieldMapping {
    private String from;
    private String to;
    private FieldAction action;
    private TransformRule transform;
    private String constantValue;
    private boolean required;
    private Integer padLength;
    private String padChar;
    private Integer decimalPlaces;
    private String dateFormatIn;
    private String dateFormatOut;
    private Map<String, String> enumMap;
    private String auditNote;
}
