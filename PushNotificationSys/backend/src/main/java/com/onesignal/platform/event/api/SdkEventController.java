package com.onesignal.platform.event.api;

import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.event.EventBatchIngestMessage;
import com.onesignal.platform.event.infrastructure.EventIngestProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
public class SdkEventController {

    private final EventIngestProducer eventIngestProducer;

    @PostMapping("/events")
    public ResponseEntity<SdkEventDto.IngestEventsResponse> ingestEvents(
            @Valid @RequestBody SdkEventDto.IngestEventsRequest request
    ) {
        UUID appId = TenantContextHolder.getAppId();
        if (appId == null) {
            throw new UnauthorizedException("Missing authenticated app scope");
        }

        int count = request.getEvents() != null ? request.getEvents().size() : 0;
        log.info("Ingesting {} SDK client events for app {}", count, appId);

        eventIngestProducer.sendEventBatch(EventBatchIngestMessage.builder()
                .appId(appId)
                .events(request.getEvents())
                .build());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                SdkEventDto.IngestEventsResponse.builder()
                        .acceptedCount(count)
                        .status("QUEUED")
                        .build()
        );
    }
}
