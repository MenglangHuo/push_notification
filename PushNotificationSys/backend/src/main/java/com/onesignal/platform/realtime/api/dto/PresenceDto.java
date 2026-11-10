package com.onesignal.platform.realtime.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

public class PresenceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresenceStatsResponse implements Serializable {
        private UUID appId;
        private long onlineCount;
        private long localSessionCount;
    }
}
