package com.nkd.nexbridge.discovery;

import java.time.Instant;
import java.util.List;

public record DependencyGraph(
        List<SystemNode> nodes,
        List<SystemDependency> edges,
        int totalSystems,
        int totalConnections,
        Instant generatedAt
) {}
