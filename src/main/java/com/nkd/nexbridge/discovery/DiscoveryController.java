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

    @GetMapping("/discovery")
    public NexResponse<DiscoveryResult> discover(@RequestParam String sistema) {
        log.info("DiscoveryController: GET /discovery?sistema={}", sistema);
        DiscoveryResult result = discoveryService.discover(sistema);
        return NexResponse.ok(result, buildMeta());
    }

    @GetMapping("/discovery/jobs")
    public NexResponse<List<JclJobScanner.JclJob>> discoverJobs(@RequestParam String sistema) {
        log.info("DiscoveryController: GET /discovery/jobs?sistema={}", sistema);
        DiscoveryResult result = discoveryService.discover(sistema);
        List<JclJobScanner.JclJob> jobs = result.getJcls().stream()
                .map(name -> new JclJobScanner.JclJob(name, null, null, null))
                .toList();
        return NexResponse.ok(jobs, buildMeta());
    }
}
