package com.nkd.nexbridge.discovery;

import java.util.List;

public record SystemNode(
        String systemId,
        String systemType,
        String name,
        List<String> programs,
        List<String> datasets,
        List<String> sensitiveFields
) {}
