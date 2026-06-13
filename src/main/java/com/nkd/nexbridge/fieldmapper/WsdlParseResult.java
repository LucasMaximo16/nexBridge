package com.nkd.nexbridge.fieldmapper;

import java.util.List;
import java.util.Map;

public record WsdlParseResult(
        List<String> operations,
        Map<String, List<SchemaField>> inputFields,
        Map<String, List<SchemaField>> outputFields
) {}
