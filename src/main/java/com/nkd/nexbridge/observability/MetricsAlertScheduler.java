package com.nkd.nexbridge.observability;

import com.nkd.nexbridge.governance.AlertLevel;
import com.nkd.nexbridge.governance.AlertService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsAlertScheduler {

    private final MeterRegistry meterRegistry;
    private final AlertService alertService;
    private final MetricsAlertConfig metricsAlertConfig;

    @Scheduled(fixedDelayString = "#{@metricsAlertConfig.checkIntervalSeconds * 1000}")
    public void checkMetrics() {
        try {
            Counter totalCounter = meterRegistry.find("nexbridge.requests.total").counter();
            Counter errorCounter = meterRegistry.find("nexbridge.requests.error").counter();
            Timer   latencyTimer = meterRegistry.find("nexbridge.request.latency").timer();

            double total  = totalCounter != null ? totalCounter.count() : 0.0;
            double errors = errorCounter != null ? errorCounter.count() : 0.0;

            double errorRate = total > 0.0 ? errors / total : 0.0;
            double p99       = latencyTimer != null ? latencyTimer.percentile(0.99, TimeUnit.MILLISECONDS) : 0.0;

            double rateThreshold    = metricsAlertConfig.getErrorRateThreshold();
            double latencyThreshold = metricsAlertConfig.getLatencyP99ThresholdMs();

            if (errorRate > rateThreshold) {
                alertService.sendAlert(
                        AlertLevel.CRITICAL,
                        "High Error Rate",
                        "Error rate " + errorRate + " exceeds threshold " + rateThreshold,
                        Map.of(
                                "errorRate",  String.valueOf(errorRate),
                                "threshold",  String.valueOf(rateThreshold)
                        )
                );
            }

            if (p99 > latencyThreshold) {
                alertService.sendAlert(
                        AlertLevel.WARNING,
                        "High P99 Latency",
                        "P99 latency " + p99 + "ms exceeds " + latencyThreshold + "ms",
                        Map.of(
                                "p99Ms",       String.valueOf(p99),
                                "thresholdMs", String.valueOf(latencyThreshold)
                        )
                );
            }

        } catch (Exception e) {
            log.warn("MetricsAlertScheduler.checkMetrics failed: {}", e.getMessage());
        }
    }
}
