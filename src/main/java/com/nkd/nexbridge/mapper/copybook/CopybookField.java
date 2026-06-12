package com.nkd.nexbridge.mapper.copybook;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class CopybookField {
    private String name;
    private String cobolName;
    private int offset;
    private int length;
    private CobolType cobolType;
    private String targetType;
    private boolean sensitive;
    private String maskType;
    private Map<String, String> enumMap;
    private Integer decimalPlaces;
    private String formatIn;
    private boolean trim;
    private boolean redefines;
    private String redefinesTarget;
    private int occursCount;
}
