package com.nkd.nexbridge.discovery;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Builder
@Data
public class DiscoveryResult {
    private String sistema;
    private List<String> programasCobol;
    private List<String> datasetsVsam;
    private List<String> jcls;
    private List<String> shadowIntegrations;
    private List<String> dadosSensiveis;
    private int totalProgramas;
    private int totalLinhasCobol;
    private Instant scannedAt;
}
