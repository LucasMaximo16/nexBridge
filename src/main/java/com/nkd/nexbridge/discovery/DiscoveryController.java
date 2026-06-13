package com.nkd.nexbridge.discovery;

import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.config.NexBridgeProperties;
import com.nkd.nexbridge.discovery.scanner.JclJobScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final NexBridgeProperties properties;

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }

    // RF-001 a RF-007: Discovery completo de um sistema
    @GetMapping("/discovery")
    public NexResponse<DiscoveryResult> discover(@RequestParam String sistema) {
        log.info("DiscoveryController: GET /discovery?sistema={}", sistema);
        return NexResponse.ok(discoveryService.discover(sistema), buildMeta());
    }

    // RF-004: Inventário completo de JCL jobs com todos os campos
    @GetMapping("/discovery/jobs")
    public NexResponse<List<JclJobScanner.JclJob>> discoverJobs(@RequestParam String sistema) {
        log.info("DiscoveryController: GET /discovery/jobs?sistema={}", sistema);
        return NexResponse.ok(discoveryService.discoverJobs(sistema), buildMeta());
    }

    // RF-008: Mapa de dependências entre sistemas (grafo dirigido)
    @GetMapping("/discovery/dependencies")
    public NexResponse<DependencyGraph> discoverDependencies() {
        log.info("DiscoveryController: GET /discovery/dependencies");
        return NexResponse.ok(discoveryService.buildDependencyGraph(), buildMeta());
    }
}
