package com.onesignal.platform.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordDispatch(String provider, String status, long latencyMs) {
        Counter.builder("push.notifications.dispatched.total")
                .description("Total number of push notifications dispatched to downstream providers")
                .tag("provider", provider != null ? provider : "UNKNOWN")
                .tag("status", status != null ? status : "UNKNOWN")
                .register(meterRegistry)
                .increment();

        if (latencyMs > 0) {
            Timer.builder("push.notifications.dispatch.latency")
                    .description("Push notification dispatch latency in milliseconds")
                    .tag("provider", provider != null ? provider : "UNKNOWN")
                    .register(meterRegistry)
                    .record(Duration.ofMillis(latencyMs));
        }
    }

    public void recordEventIngest(String eventType) {
        Counter.builder("push.events.ingested.total")
                .description("Total number of client SDK telemetry events ingested")
                .tag("eventType", eventType != null ? eventType : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimitExceeded(String type) {
        Counter.builder("push.rate_limit.exceeded.total")
                .description("Total rate limit rejections")
                .tag("type", type != null ? type : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }
}
