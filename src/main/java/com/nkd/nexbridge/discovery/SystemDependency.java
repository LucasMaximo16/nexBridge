package com.nkd.nexbridge.discovery;

public record SystemDependency(
        String fromSystem,
        String toSystem,
        String protocol,
        String description
) {}
