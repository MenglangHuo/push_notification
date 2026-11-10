package com.onesignal.platform.event.event;

import com.onesignal.platform.event.api.dto.SdkEventDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventBatchIngestMessage implements Serializable {
    private UUID appId;
    private List<SdkEventDto.EventItem> events;
    @Builder.Default
    private Instant enqueuedAt = Instant.now();
}
